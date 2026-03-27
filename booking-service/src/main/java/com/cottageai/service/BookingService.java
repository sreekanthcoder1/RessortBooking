package com.cottageai.service;

import com.cottageai.entity.Booking;
import com.cottageai.entity.Booking.BookingStatus;
import com.cottageai.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RestTemplate restTemplate;

    @Value("${app.services.cottage-service}")
    private String cottageServiceUrl;

    @Value("${app.services.notification-service}")
    private String notificationServiceUrl;

    // ════════════════════════════════════════════════════════════
    // CREATE
    // ════════════════════════════════════════════════════════════

    @Transactional
    public Booking createBooking(Long userId, String userName, String userEmail,
                                 Long cottageId, LocalDate checkIn, LocalDate checkOut) {

        if (!checkIn.isBefore(checkOut))
            throw new RuntimeException("Check-out must be after check-in");
        if (checkIn.isBefore(LocalDate.now()))
            throw new RuntimeException("Check-in cannot be in the past");

        long conflicts = bookingRepository.countConflictingBookings(cottageId, checkIn, checkOut);
        if (conflicts > 0)
            throw new RuntimeException("Dates are no longer available. Please choose different dates.");

        Map<String, Object> avail = fetchAvailability(cottageId, checkIn, checkOut);
        if (!Boolean.TRUE.equals(avail.get("available")))
            throw new RuntimeException("Cottage is not available for selected dates");

        Map<String, Object> cottageInfo = fetchCottageInfo(cottageId);

        int nights = (int) checkIn.until(checkOut).getDays();
        BigDecimal ppn = new BigDecimal(avail.get("pricePerNight").toString());
        BigDecimal total = ppn.multiply(BigDecimal.valueOf(nights));
        String name = cottageInfo.getOrDefault("name", "Cottage").toString();
        Long adminId = cottageInfo.containsKey("adminId")
                ? Long.valueOf(cottageInfo.get("adminId").toString()) : null;

        Booking booking = Booking.builder()
                .bookingRef(generateRef())
                .userId(userId).cottageId(cottageId).adminId(adminId)
                .cottageName(name).checkIn(checkIn).checkOut(checkOut)
                .totalNights(nights).pricePerNight(ppn).totalAmount(total)
                .userName(userName).userEmail(userEmail)
                .status(BookingStatus.PENDING)
                .build();

        booking = bookingRepository.save(booking);
        log.info("Booking created: ref={} cottage={}", booking.getBookingRef(), cottageId);
        return booking;
    }

    // ════════════════════════════════════════════════════════════
    // STATUS CHANGES
    // ════════════════════════════════════════════════════════════

    @Transactional
    public Booking confirmBooking(Long bookingId) {
        Booking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        b.setStatus(BookingStatus.CONFIRMED);
        b = bookingRepository.save(b);
        notifyConfirmation(b);
        return b;
    }

    @Transactional
    public Booking cancelBooking(Long bookingId, Long userId) {
        Booking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        if (!b.getUserId().equals(userId))
            throw new RuntimeException("You cannot cancel someone else's booking");
        if (b.getStatus() == BookingStatus.CANCELLED)
            throw new RuntimeException("Already cancelled");
        b.setStatus(BookingStatus.CANCELLED);
        return bookingRepository.save(b);
    }

    // ════════════════════════════════════════════════════════════
    // QUERIES
    // ════════════════════════════════════════════════════════════

    public Page<Booking> getUserBookings(Long userId, int page, int size) {
        return bookingRepository.findByUserId(userId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    /** ADMIN sees only their own cottage bookings. */
    public Page<Booking> getAdminBookings(Long adminId, int page, int size) {
        return bookingRepository.findByAdminId(adminId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    /** SUPER_ADMIN sees all. */
    public Page<Booking> getAllBookings(int page, int size) {
        return bookingRepository.findAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    public Booking getBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
    }

    public Booking getBookingByRef(String ref) {
        return bookingRepository.findByBookingRef(ref)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + ref));
    }

    // ════════════════════════════════════════════════════════════
    // STATS — earnings + counts scoped by role
    // ════════════════════════════════════════════════════════════

    /**
     * Returns booking stats.
     * ADMIN:       only their own cottage bookings.
     * SUPER_ADMIN: entire platform.
     */
    public Map<String, Object> getStats(Long adminId, boolean isSuperAdmin) {
        List<Booking> bookings = isSuperAdmin
                ? bookingRepository.findAll()
                : bookingRepository.findAllByAdminId(adminId);

        long total     = bookings.size();
        long confirmed = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count();
        long pending   = bookings.stream().filter(b -> b.getStatus() == BookingStatus.PENDING).count();
        long cancelled = bookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count();

        BigDecimal revenue = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .map(Booking::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "totalBookings",     total,
                "confirmedBookings", confirmed,
                "pendingBookings",   pending,
                "cancelledBookings", cancelled,
                "totalRevenue",      revenue
        );
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchAvailability(Long cottageId, LocalDate ci, LocalDate co) {
        try {
            String url = cottageServiceUrl + "/api/cottages/" + cottageId
                    + "/availability?checkIn=" + ci + "&checkOut=" + co;
            ResponseEntity<Map> r = restTemplate.getForEntity(url, Map.class);
            return r.getBody() != null ? r.getBody() : Map.of("available", false);
        } catch (Exception e) {
            log.error("Availability fetch failed: {}", e.getMessage());
            return Map.of("available", true, "pricePerNight", 5000);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchCottageInfo(Long cottageId) {
        try {
            String url = cottageServiceUrl + "/api/cottages/" + cottageId;
            ResponseEntity<Map> r = restTemplate.getForEntity(url, Map.class);
            return r.getBody() != null ? r.getBody() : Map.of();
        } catch (Exception e) {
            log.warn("Cottage info fetch failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private void notifyConfirmation(Booking b) {
        try {
            Map<String, Object> n = new HashMap<>();
            n.put("userEmail",   b.getUserEmail());
            n.put("userName",    b.getUserName());
            n.put("bookingRef",  b.getBookingRef());
            n.put("cottageName", b.getCottageName());
            n.put("checkIn",     b.getCheckIn().toString());
            n.put("checkOut",    b.getCheckOut().toString());
            n.put("totalNights", b.getTotalNights());
            n.put("totalAmount", b.getTotalAmount().doubleValue());
            restTemplate.postForEntity(
                    notificationServiceUrl + "/api/notifications/booking-confirmed",
                    n, Void.class);
        } catch (Exception e) {
            log.warn("Notification failed (non-critical): {}", e.getMessage());
        }
    }

    private String generateRef() {
        return "CAI" + (System.currentTimeMillis() % 1_000_000)
                + (char)('A' + new Random().nextInt(26));
    }
}
