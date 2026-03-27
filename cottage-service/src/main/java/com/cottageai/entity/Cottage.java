package com.cottageai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cottages")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cottage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String location;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "daily_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyPrice;

    @Column(name = "max_guests")
    @Builder.Default
    private Integer maxGuests = 2;

    @Column(name = "bedrooms")
    @Builder.Default
    private Integer bedrooms = 1;

    @Column(name = "bathrooms")
    @Builder.Default
    private Integer bathrooms = 1;

    @Column(name = "amenities", columnDefinition = "TEXT")
    private String amenities; // comma-separated list

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = false;

    @Column(name = "is_activated")
    @Builder.Default
    private Boolean isActivated = false;

    @OneToMany(mappedBy = "cottage", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CottagePhoto> photos = new ArrayList<>();

    @OneToMany(mappedBy = "cottage", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CottageAvailability> availabilities = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
