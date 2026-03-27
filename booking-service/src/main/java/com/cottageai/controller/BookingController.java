package com.cottageai.controller;

import com.cottageai.entity.Booking;
import com.cottageai.service.BookingService;
import com.cottageai.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class BookingController {

    private final BookingService bookingService;
    private final JwtUtil jwtUtil;

    // ═══ USER ENDPOINTS ═══════════════════════════════════════

    @PostMapping
    public ResponseEntity<Booking> createBooking(
            @RequestBody BookingRequest req,
            HttpServletRequest request) {
        Long userId = extractUserId(request);
        String email = extractEmail(request);
        return ResponseEntity.ok(bookingService.createBooking(
                userId, req.getUserName(), email,
                req.getCottageId(), req.getCheckIn(), req.getCheckOut()));
    }

    @GetMapping("/my")
    public ResponseEntity<Page<Booking>> myBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        return ResponseEntity.ok(bookingService.getUserBookings(extractUserId(request), page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Booking> getBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.getBooking(id));
    }

    @GetMapping("/ref/{ref}")
    public ResponseEntity<Booking> getByRef(@PathVariable String ref) {
        return ResponseEntity.ok(bookingService.getBookingByRef(ref));
    }

    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<Booking> cancelBooking(
            @PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(bookingService.cancelBooking(id, extractUserId(request)));
    }

    // ═══ ADMIN ENDPOINTS ═══════════════════════════════════════
    // ADMIN    → sees only bookings for their own cottages
    // SUPER_ADMIN → sees ALL bookings on the platform

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Page<Booking>> adminBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long requesterId = extractUserId(request);
        boolean isSuperAdmin = isSuperAdmin(request);
        if (isSuperAdmin) {
            return ResponseEntity.ok(bookingService.getAllBookings(page, size));
        }
        return ResponseEntity.ok(bookingService.getAdminBookings(requesterId, page, size));
    }

    /** Admin stats — earnings + counts for their own cottages only (or all for SUPER_ADMIN). */
    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> adminStats(HttpServletRequest request) {
        Long requesterId = extractUserId(request);
        boolean isSuperAdmin = isSuperAdmin(request);
        return ResponseEntity.ok(bookingService.getStats(requesterId, isSuperAdmin));
    }

    // ═══ INTERNAL ════════════════════════════════════════════

    @PostMapping("/internal/confirm/{bookingId}")
    public ResponseEntity<Booking> confirmBooking(@PathVariable Long bookingId) {
        return ResponseEntity.ok(bookingService.confirmBooking(bookingId));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "booking-service"));
    }

    // ─── helpers ──────────────────────────────────────────────
    private Long extractUserId(HttpServletRequest req) {
        return jwtUtil.getUserIdFromToken(extractToken(req));
    }
    private String extractEmail(HttpServletRequest req) {
        return jwtUtil.getEmailFromToken(extractToken(req));
    }
    private boolean isSuperAdmin(HttpServletRequest req) {
        return "SUPER_ADMIN".equals(jwtUtil.getRoleFromToken(extractToken(req)));
    }
    private String extractToken(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) return h.substring(7);
        throw new RuntimeException("Missing Authorization header");
    }

    @Data static class BookingRequest {
        private Long cottageId;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate checkIn;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate checkOut;
        private String userName;
    }
}
