package com.cottageai.controller;

import com.cottageai.dto.BookingNotification;
import com.cottageai.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class NotificationController {

    private final EmailService emailService;

    @PostMapping("/booking-confirmed")
    public ResponseEntity<Map<String, String>> bookingConfirmed(
            @RequestBody BookingNotification notification) {
        emailService.sendBookingConfirmationToUser(notification);
        emailService.sendBookingNotificationToAdmin(notification);
        return ResponseEntity.ok(Map.of("message", "Notifications queued"));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "notification-service"));
    }
}
