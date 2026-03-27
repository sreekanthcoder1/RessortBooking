package com.cottageai;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import jakarta.persistence.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import com.cottageai.security.JwtAuthFilter;
import com.cottageai.util.JwtUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

// ═══ MAIN ═══════════════════════════════════════════════════
@SpringBootApplication
@EnableJpaAuditing
class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}

// ═══ ENTITY ══════════════════════════════════════════════════
@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "razorpay_order_id")
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    @Column(name = "amount", precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency")
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type")
    @Builder.Default
    private PaymentType paymentType = PaymentType.BOOKING;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    enum PaymentStatus { PENDING, SUCCESS, FAILED, REFUNDED }
    enum PaymentType { BOOKING, ACTIVATION }
}

// ═══ REPOSITORY ══════════════════════════════════════════════
@Repository
interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByRazorpayOrderId(String orderId);
    Optional<Payment> findByBookingId(Long bookingId);
}

// ═══ SERVICE ═════════════════════════════════════════════════
@Service
@RequiredArgsConstructor
@Slf4j
class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RestTemplate restTemplate;

    @Value("${app.razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${app.razorpay.key-secret}")
    private String razorpayKeySecret;

    @Value("${app.services.booking-service}")
    private String bookingServiceUrl;

    @Value("${app.services.cottage-service}")
    private String cottageServiceUrl;

    @Value("${app.services.notification-service}")
    private String notificationServiceUrl;

    // Create Razorpay order for booking payment
    @Transactional
    public Map<String, Object> createBookingOrder(Long bookingId, BigDecimal amount) throws RazorpayException {
        RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

        JSONObject orderReq = new JSONObject();
        orderReq.put("amount", amount.multiply(BigDecimal.valueOf(100)).intValue()); // paise
        orderReq.put("currency", "INR");
        orderReq.put("receipt", "booking_" + bookingId);

        Order order = client.orders.create(orderReq);
        String orderId = order.get("id");

        Payment payment = Payment.builder()
                .bookingId(bookingId)
                .razorpayOrderId(orderId)
                .amount(amount)
                .paymentType(Payment.PaymentType.BOOKING)
                .build();
        paymentRepository.save(payment);

        return Map.of(
                "orderId", orderId,
                "amount", amount,
                "currency", "INR",
                "keyId", razorpayKeyId
        );
    }

    // Create order for admin cottage activation
    @Transactional
    public Map<String, Object> createActivationOrder(Long cottageId) throws RazorpayException {
        BigDecimal activationFee = new BigDecimal("999.00");
        RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

        JSONObject orderReq = new JSONObject();
        orderReq.put("amount", 99900); // ₹999 in paise
        orderReq.put("currency", "INR");
        orderReq.put("receipt", "activation_" + cottageId);

        Order order = client.orders.create(orderReq);

        Payment payment = Payment.builder()
                .bookingId(cottageId) // reuse field for cottageId
                .razorpayOrderId(order.get("id"))
                .amount(activationFee)
                .paymentType(Payment.PaymentType.ACTIVATION)
                .build();
        paymentRepository.save(payment);

        return Map.of(
                "orderId", order.get("id"),
                "amount", activationFee,
                "currency", "INR",
                "keyId", razorpayKeyId
        );
    }

    // Verify Razorpay payment signature
    @Transactional
    public Map<String, Object> verifyPayment(String razorpayOrderId,
                                              String razorpayPaymentId,
                                              String signature) {
        // Signature verification
        try {
            String data = razorpayOrderId + "|" + razorpayPaymentId;
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    razorpayKeySecret.getBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) hexString.append(String.format("%02x", b));
            String generatedSignature = hexString.toString();

            Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                    .orElseThrow(() -> new RuntimeException("Payment record not found"));

            if (!generatedSignature.equals(signature)) {
                payment.setStatus(Payment.PaymentStatus.FAILED);
                paymentRepository.save(payment);
                return Map.of("success", false, "message", "Payment signature invalid");
            }

            // Mark payment success
            payment.setStatus(Payment.PaymentStatus.SUCCESS);
            payment.setRazorpayPaymentId(razorpayPaymentId);
            paymentRepository.save(payment);

            // Trigger downstream based on type
            if (payment.getPaymentType() == Payment.PaymentType.BOOKING) {
                confirmBooking(payment.getBookingId());
            } else if (payment.getPaymentType() == Payment.PaymentType.ACTIVATION) {
                activateCottage(payment.getBookingId()); // bookingId field holds cottageId
            }

            return Map.of("success", true, "message", "Payment verified successfully");

        } catch (Exception e) {
            log.error("Payment verification error: {}", e.getMessage());
            return Map.of("success", false, "message", "Verification failed: " + e.getMessage());
        }
    }

    private void confirmBooking(Long bookingId) {
        try {
            restTemplate.postForEntity(
                    bookingServiceUrl + "/api/bookings/internal/confirm/" + bookingId,
                    null, Void.class);
        } catch (Exception e) {
            log.error("Failed to confirm booking {}: {}", bookingId, e.getMessage());
        }
    }

    private void activateCottage(Long cottageId) {
        try {
            restTemplate.postForEntity(
                    cottageServiceUrl + "/api/cottages/internal/activate/" + cottageId,
                    null, Void.class);
        } catch (Exception e) {
            log.error("Failed to activate cottage {}: {}", cottageId, e.getMessage());
        }
    }
}

// ═══ CONTROLLER ══════════════════════════════════════════════
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
class PaymentController {

    private final PaymentService paymentService;
    private final JwtUtil jwtUtil;

    @PostMapping("/booking/create-order")
    public ResponseEntity<Map<String, Object>> createBookingOrder(
            @RequestBody Map<String, Object> req) throws RazorpayException {
        Long bookingId = Long.valueOf(req.get("bookingId").toString());
        BigDecimal amount = new BigDecimal(req.get("amount").toString());
        return ResponseEntity.ok(paymentService.createBookingOrder(bookingId, amount));
    }

    @PostMapping("/activation/create-order")
    public ResponseEntity<Map<String, Object>> createActivationOrder(
            @RequestBody Map<String, Object> req) throws RazorpayException {
        Long cottageId = Long.valueOf(req.get("cottageId").toString());
        return ResponseEntity.ok(paymentService.createActivationOrder(cottageId));
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(
            @RequestBody Map<String, String> req) {
        return ResponseEntity.ok(paymentService.verifyPayment(
                req.get("razorpayOrderId"),
                req.get("razorpayPaymentId"),
                req.get("razorpaySignature")
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "payment-service"));
    }
}

// ═══ CONFIG ══════════════════════════════════════════════════
@Configuration
class PaymentConfig {
    @Bean public RestTemplate restTemplate() { return new RestTemplate(); }
}

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
class PaymentSecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/payments/health", "/api/payments/verify").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
