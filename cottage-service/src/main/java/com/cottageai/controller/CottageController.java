package com.cottageai.controller;

import com.cottageai.dto.CottageRequest;
import com.cottageai.entity.Cottage;
import com.cottageai.service.CottageService;
import com.cottageai.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cottages")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class CottageController {

    private final CottageService cottageService;
    private final JwtUtil jwtUtil;

    // ═══════════════════════════════════════════════════════════
    // PUBLIC — no auth needed
    // ═══════════════════════════════════════════════════════════

    @GetMapping
    public ResponseEntity<Page<Cottage>> listCottages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(cottageService.listActiveCottages(page, size));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<Cottage>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(cottageService.searchCottages(query, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Cottage> getCottage(@PathVariable Long id) {
        return ResponseEntity.ok(cottageService.getPublicCottage(id));
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<Map<String, Object>> checkAvailability(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
        return ResponseEntity.ok(cottageService.checkAvailabilityAndPrice(id, checkIn, checkOut));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "cottage-service"));
    }

    // ═══════════════════════════════════════════════════════════
    // ADMIN — own cottages only; SUPER_ADMIN bypasses ownership
    // ═══════════════════════════════════════════════════════════

    /** Returns admin's own cottage. SUPER_ADMIN returns nothing here (uses /superadmin/all) */
    @GetMapping("/admin/my-cottage")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> getAdminCottage(HttpServletRequest request) {
        Long adminId = extractUserId(request);
        return cottageService.getAdminCottage(adminId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** ADMIN creates ONE cottage (enforced in service). SUPER_ADMIN can create unlimited, free. */
    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Cottage> createCottage(
            @Valid @RequestBody CottageRequest req,
            HttpServletRequest request) {
        Long adminId = extractUserId(request);
        boolean isSuperAdmin = isSuperAdmin(request);
        return ResponseEntity.ok(cottageService.createCottage(adminId, req, isSuperAdmin));
    }

    /** ADMIN can only update their OWN cottage. SUPER_ADMIN can update any. */
    @PutMapping("/admin/{cottageId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Cottage> updateCottage(
            @PathVariable Long cottageId,
            @Valid @RequestBody CottageRequest req,
            HttpServletRequest request) {
        Long adminId = extractUserId(request);
        boolean isSuperAdmin = isSuperAdmin(request);
        return ResponseEntity.ok(cottageService.updateCottage(adminId, cottageId, req, isSuperAdmin));
    }

    /**
     * Upload photos — FIXED multipart handling.
     * Uses List<MultipartFile> with @RequestParam("files").
     */
    @PostMapping(value = "/admin/{cottageId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> uploadPhotos(
            @PathVariable Long cottageId,
            @RequestParam("files") List<MultipartFile> files,
            HttpServletRequest request) {
        Long adminId = extractUserId(request);
        boolean isSuperAdmin = isSuperAdmin(request);
        try {
            List<String> urls = cottageService.uploadPhotos(adminId, cottageId, files, isSuperAdmin);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "uploaded", urls.size(),
                    "urls", urls
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/admin/{cottageId}/photos/{photoId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deletePhoto(
            @PathVariable Long cottageId,
            @PathVariable Long photoId,
            HttpServletRequest request) {
        cottageService.deletePhoto(extractUserId(request), cottageId, photoId, isSuperAdmin(request));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/admin/{cottageId}/photos/{photoId}/primary")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> setPrimaryPhoto(
            @PathVariable Long cottageId,
            @PathVariable Long photoId,
            HttpServletRequest request) {
        cottageService.setPrimaryPhoto(extractUserId(request), cottageId, photoId, isSuperAdmin(request));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/admin/{cottageId}/availability")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> manageDates(
            @PathVariable Long cottageId,
            @RequestBody DateRangeRequest req,
            HttpServletRequest request) {
        cottageService.blockDates(extractUserId(request), cottageId, req.getFrom(), req.getTo(), req.getIsBlocked());
        return ResponseEntity.ok(Map.of("message", "Availability updated"));
    }

    @PostMapping("/admin/{cottageId}/pricing")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> setCustomPrice(
            @PathVariable Long cottageId,
            @RequestBody CustomPriceRequest req,
            HttpServletRequest request) {
        cottageService.setCustomPrice(extractUserId(request), cottageId, req.getFrom(), req.getTo(), req.getPrice());
        return ResponseEntity.ok(Map.of("message", "Custom pricing set"));
    }

    /** Toggle active. ADMIN can only toggle their own; SUPER_ADMIN can toggle any. */
    @PatchMapping("/admin/{cottageId}/toggle")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> toggleActive(
            @PathVariable Long cottageId,
            @RequestParam boolean active,
            HttpServletRequest request) {
        cottageService.toggleActive(extractUserId(request), cottageId, active, isSuperAdmin(request));
        return ResponseEntity.ok(Map.of("message", active ? "Cottage activated" : "Cottage deactivated"));
    }

    // ═══════════════════════════════════════════════════════════
    // SUPER ADMIN ONLY
    // ═══════════════════════════════════════════════════════════

    /** All cottages across all admins with pagination */
    @GetMapping("/superadmin/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Page<Cottage>> getAllCottages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(cottageService.getAllCottagesPaged(page, size));
    }

    /** Super admin delete any cottage */
    @DeleteMapping("/superadmin/{cottageId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> deleteCottage(@PathVariable Long cottageId) {
        cottageService.deleteCottage(cottageId);
        return ResponseEntity.ok(Map.of("message", "Cottage deleted"));
    }

    // ═══════════════════════════════════════════════════════════
    // INTERNAL (service-to-service)
    // ═══════════════════════════════════════════════════════════

    @PostMapping("/internal/activate/{cottageId}")
    public ResponseEntity<Void> activateCottage(@PathVariable Long cottageId) {
        cottageService.activateCottage(cottageId);
        return ResponseEntity.ok().build();
    }

    // ─── helpers ──────────────────────────────────────────────
    private Long extractUserId(HttpServletRequest request) {
        return jwtUtil.getUserIdFromToken(extractToken(request));
    }

    private boolean isSuperAdmin(HttpServletRequest request) {
        return "SUPER_ADMIN".equals(jwtUtil.getRoleFromToken(extractToken(request)));
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) return bearer.substring(7);
        throw new RuntimeException("No Authorization header");
    }

    @Data static class DateRangeRequest {
        @NotNull private LocalDate from;
        @NotNull private LocalDate to;
        private Boolean isBlocked = true;
    }

    @Data static class CustomPriceRequest {
        @NotNull private LocalDate from;
        @NotNull private LocalDate to;
        @NotNull private BigDecimal price;
    }
}
