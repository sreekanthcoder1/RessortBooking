package com.cottageai.service;

import com.cottageai.dto.CottageRequest;
import com.cottageai.entity.Cottage;
import com.cottageai.entity.CottageAvailability;
import com.cottageai.entity.CottagePhoto;
import com.cottageai.exception.CottageException;
import com.cottageai.repository.CottageAvailabilityRepository;
import com.cottageai.repository.CottagePhotoRepository;
import com.cottageai.repository.CottageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CottageService {

    private final CottageRepository cottageRepository;
    private final CottagePhotoRepository photoRepository;
    private final CottageAvailabilityRepository availabilityRepository;

    @Value("${app.upload.dir:uploads/cottages}")
    private String uploadDir;

    // ════════════════════════════════════════════════════════════
    // CREATE
    // ════════════════════════════════════════════════════════════

    /**
     * ADMIN: enforced 1 cottage per account. isActive=false, isActivated=false → needs payment.
     * SUPER_ADMIN: unlimited, immediately active, no payment.
     */
    @Transactional
    public Cottage createCottage(Long adminId, CottageRequest req, boolean isSuperAdmin) {
        if (!isSuperAdmin) {
            if (cottageRepository.existsByAdminId(adminId)) {
                throw new CottageException("You already own a cottage. Edit it from your dashboard.");
            }
        }

        Cottage cottage = Cottage.builder()
                .adminId(adminId)
                .name(req.getName())
                .description(req.getDescription())
                .location(req.getLocation())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .dailyPrice(req.getDailyPrice())
                .maxGuests(req.getMaxGuests() != null ? req.getMaxGuests() : 4)
                .bedrooms(req.getBedrooms() != null ? req.getBedrooms() : 2)
                .bathrooms(req.getBathrooms() != null ? req.getBathrooms() : 1)
                .amenities(req.getAmenities())
                // SUPER_ADMIN → immediately live; ADMIN → requires activation payment
                .isActive(isSuperAdmin)
                .isActivated(isSuperAdmin)
                .build();

        cottage = cottageRepository.save(cottage);
        log.info("Cottage created: id={} adminId={} superAdmin={}", cottage.getId(), adminId, isSuperAdmin);
        return cottage;
    }

    // ════════════════════════════════════════════════════════════
    // UPDATE
    // ════════════════════════════════════════════════════════════

    /**
     * ADMIN: can only update their OWN cottage.
     * SUPER_ADMIN: can update ANY cottage.
     */
    @Transactional
    public Cottage updateCottage(Long requesterId, Long cottageId, CottageRequest req, boolean isSuperAdmin) {
        Cottage cottage = isSuperAdmin
                ? cottageRepository.findById(cottageId)
                .orElseThrow(() -> new CottageException("Cottage not found"))
                : findOwnedCottage(requesterId, cottageId);

        cottage.setName(req.getName());
        cottage.setDescription(req.getDescription());
        cottage.setLocation(req.getLocation());
        if (req.getLatitude() != null) cottage.setLatitude(req.getLatitude());
        if (req.getLongitude() != null) cottage.setLongitude(req.getLongitude());
        cottage.setDailyPrice(req.getDailyPrice());
        if (req.getMaxGuests() != null) cottage.setMaxGuests(req.getMaxGuests());
        if (req.getBedrooms() != null) cottage.setBedrooms(req.getBedrooms());
        if (req.getBathrooms() != null) cottage.setBathrooms(req.getBathrooms());
        if (req.getAmenities() != null) cottage.setAmenities(req.getAmenities());

        return cottageRepository.save(cottage);
    }

    // ════════════════════════════════════════════════════════════
    // DELETE (SUPER_ADMIN only)
    // ════════════════════════════════════════════════════════════

    @Transactional
    public void deleteCottage(Long cottageId) {
        Cottage cottage = cottageRepository.findById(cottageId)
                .orElseThrow(() -> new CottageException("Cottage not found"));
        availabilityRepository.deleteByCottageId(cottageId);
        photoRepository.deleteByCottageId(cottageId);
        cottageRepository.delete(cottage);
        log.info("Cottage {} deleted by super admin", cottageId);
    }

    // ════════════════════════════════════════════════════════════
    // READ
    // ════════════════════════════════════════════════════════════

    /** Returns the SINGLE cottage owned by this admin. */
    public Optional<Cottage> getAdminCottage(Long adminId) {
        return cottageRepository.findByAdminId(adminId);
    }

    /** Public detail view — only activated + active cottages. */
    public Cottage getPublicCottage(Long cottageId) {
        Cottage c = cottageRepository.findById(cottageId)
                .orElseThrow(() -> new CottageException("Cottage not found"));
        if (!c.getIsActive() || !c.getIsActivated()) {
            throw new CottageException("Cottage not available");
        }
        return c;
    }

    public Page<Cottage> listActiveCottages(int page, int size) {
        return cottageRepository.findByIsActiveTrueAndIsActivatedTrue(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    public Page<Cottage> searchCottages(String query, int page, int size) {
        return cottageRepository.searchCottages(query, PageRequest.of(page, size));
    }

    /** SUPER_ADMIN paginated view of all cottages. */
    public Page<Cottage> getAllCottagesPaged(int page, int size) {
        return cottageRepository.findAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    // ════════════════════════════════════════════════════════════
    // PHOTOS — fixed file save + proper ownership check
    // ════════════════════════════════════════════════════════════

    @Transactional
    public List<String> uploadPhotos(Long requesterId, Long cottageId,
                                     List<MultipartFile> files, boolean isSuperAdmin) throws IOException {
        Cottage cottage = isSuperAdmin
                ? cottageRepository.findById(cottageId)
                .orElseThrow(() -> new CottageException("Cottage not found"))
                : findOwnedCottage(requesterId, cottageId);

        List<String> savedUrls = new ArrayList<>();
        boolean isFirstBatch = photoRepository.findByCottageId(cottageId).isEmpty();

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;

            String ct = file.getContentType();
            if (ct == null || !ct.startsWith("image/")) {
                throw new CottageException("Only image files allowed. Got: " + ct);
            }

            String url = saveFileToDisk(file, cottageId);
            boolean isPrimary = isFirstBatch && savedUrls.isEmpty();

            CottagePhoto photo = CottagePhoto.builder()
                    .cottage(cottage)
                    .url(url)
                    .isPrimary(isPrimary)
                    .build();
            photoRepository.save(photo);
            savedUrls.add(url);
        }

        log.info("Uploaded {} photos for cottage {}", savedUrls.size(), cottageId);
        return savedUrls;
    }

    @Transactional
    public void deletePhoto(Long requesterId, Long cottageId, Long photoId, boolean isSuperAdmin) {
        if (!isSuperAdmin) findOwnedCottage(requesterId, cottageId);
        photoRepository.deleteById(photoId);
    }

    @Transactional
    public void setPrimaryPhoto(Long requesterId, Long cottageId, Long photoId, boolean isSuperAdmin) {
        if (!isSuperAdmin) findOwnedCottage(requesterId, cottageId);
        photoRepository.findByCottageId(cottageId).forEach(p -> {
            p.setIsPrimary(p.getId().equals(photoId));
            photoRepository.save(p);
        });
    }

    // ════════════════════════════════════════════════════════════
    // AVAILABILITY & PRICING
    // ════════════════════════════════════════════════════════════

    @Transactional
    public void blockDates(Long adminId, Long cottageId, LocalDate from, LocalDate to, Boolean block) {
        findOwnedCottage(adminId, cottageId);
        availabilityRepository.deleteByDateRange(cottageId, from, to);
        List<CottageAvailability> records = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            records.add(CottageAvailability.builder()
                    .cottage(cottageRepository.getReferenceById(cottageId))
                    .date(d)
                    .isBlocked(block != null ? block : true)
                    .build());
        }
        availabilityRepository.saveAll(records);
    }

    @Transactional
    public void setCustomPrice(Long adminId, Long cottageId, LocalDate from, LocalDate to, BigDecimal price) {
        findOwnedCottage(adminId, cottageId);
        availabilityRepository.deleteByDateRange(cottageId, from, to);
        List<CottageAvailability> records = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            records.add(CottageAvailability.builder()
                    .cottage(cottageRepository.getReferenceById(cottageId))
                    .date(d).isBlocked(false).customPrice(price).build());
        }
        availabilityRepository.saveAll(records);
    }

    public Map<String, Object> checkAvailabilityAndPrice(Long cottageId, LocalDate checkIn, LocalDate checkOut) {
        Cottage cottage = cottageRepository.findById(cottageId)
                .orElseThrow(() -> new CottageException("Cottage not found"));
        long blocked = availabilityRepository.countBlockedDates(cottageId, checkIn, checkOut);
        boolean available = blocked == 0 && cottage.getIsActive() && cottage.getIsActivated();
        int nights = (int) checkIn.until(checkOut).getDays();
        BigDecimal total = cottage.getDailyPrice().multiply(BigDecimal.valueOf(nights));
        return Map.of(
                "available", available, "checkIn", checkIn, "checkOut", checkOut,
                "totalNights", nights, "pricePerNight", cottage.getDailyPrice(),
                "totalAmount", total, "cottageId", cottageId,
                "adminId", cottage.getAdminId()
        );
    }

    // ════════════════════════════════════════════════════════════
    // TOGGLE ACTIVE / ACTIVATE
    // ════════════════════════════════════════════════════════════

    @Transactional
    public void toggleActive(Long requesterId, Long cottageId, boolean active, boolean isSuperAdmin) {
        Cottage cottage = isSuperAdmin
                ? cottageRepository.findById(cottageId)
                .orElseThrow(() -> new CottageException("Cottage not found"))
                : findOwnedCottage(requesterId, cottageId);

        if (!isSuperAdmin && !cottage.getIsActivated() && active) {
            throw new CottageException("Cottage must be activated via payment before going live.");
        }
        cottage.setIsActive(active);
        cottageRepository.save(cottage);
    }

    /** Called by payment-service after successful activation payment. */
    @Transactional
    public void activateCottage(Long cottageId) {
        Cottage cottage = cottageRepository.findById(cottageId)
                .orElseThrow(() -> new CottageException("Cottage not found"));
        cottage.setIsActivated(true);
        cottage.setIsActive(true);
        cottageRepository.save(cottage);
        log.info("Cottage {} activated after payment", cottageId);
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════

    /** Ensures the requester owns this cottage. Throws 403 if not. */
    private Cottage findOwnedCottage(Long adminId, Long cottageId) {
        Cottage c = cottageRepository.findById(cottageId)
                .orElseThrow(() -> new CottageException("Cottage not found"));
        if (!c.getAdminId().equals(adminId)) {
            throw new CottageException("Access denied: you do not own this cottage");
        }
        return c;
    }

    /** Saves file to disk, returns URL path for serving via /uploads/... */
    private String saveFileToDisk(MultipartFile file, Long cottageId) throws IOException {
        // Preserve extension
        String orig = file.getOriginalFilename();
        String ext = (orig != null && orig.contains("."))
                ? orig.substring(orig.lastIndexOf('.'))
                : ".jpg";

        String filename = "cottage_" + cottageId + "_" + UUID.randomUUID() + ext;
        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        return "/uploads/cottages/" + filename;
    }
}
