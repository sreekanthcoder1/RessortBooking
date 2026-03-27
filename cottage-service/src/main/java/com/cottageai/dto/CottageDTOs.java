package com.cottageai.dto;

import com.cottageai.entity.Cottage;
import com.cottageai.entity.CottagePhoto;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// ─── COTTAGE RESPONSE ────────────────────────────────────────
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class CottageResponse {
    private Long id;
    private Long adminId;
    private String name;
    private String description;
    private String location;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal dailyPrice;
    private Integer maxGuests;
    private Integer bedrooms;
    private Integer bathrooms;
    private String amenities;
    private Boolean isActive;
    private Boolean isActivated;
    private List<String> photoUrls;
    private String primaryPhoto;
    private LocalDateTime createdAt;

    public static CottageResponse from(Cottage c) {
        List<String> urls = c.getPhotos().stream()
                .map(CottagePhoto::getUrl)
                .toList();

        String primary = c.getPhotos().stream()
                .filter(CottagePhoto::getIsPrimary)
                .map(CottagePhoto::getUrl)
                .findFirst()
                .orElse(urls.isEmpty() ? null : urls.get(0));

        return CottageResponse.builder()
                .id(c.getId())
                .adminId(c.getAdminId())
                .name(c.getName())
                .description(c.getDescription())
                .location(c.getLocation())
                .latitude(c.getLatitude())
                .longitude(c.getLongitude())
                .dailyPrice(c.getDailyPrice())
                .maxGuests(c.getMaxGuests())
                .bedrooms(c.getBedrooms())
                .bathrooms(c.getBathrooms())
                .amenities(c.getAmenities())
                .isActive(c.getIsActive())
                .isActivated(c.getIsActivated())
                .photoUrls(urls)
                .primaryPhoto(primary)
                .createdAt(c.getCreatedAt())
                .build();
    }
}

// ─── AVAILABILITY REQUEST ────────────────────────────────────
@Data
@NoArgsConstructor
@AllArgsConstructor
class AvailabilityRequest {
    @NotNull private LocalDate from;
    @NotNull private LocalDate to;
    private Boolean isBlocked;
    private BigDecimal customPrice;
}

// ─── PRICE CHECK RESPONSE ────────────────────────────────────
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PriceCheckResponse {
    private Long cottageId;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private int totalNights;
    private BigDecimal pricePerNight;
    private BigDecimal totalAmount;
    private boolean available;
}

// ─── PAGE RESPONSE ───────────────────────────────────────────
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;
}
