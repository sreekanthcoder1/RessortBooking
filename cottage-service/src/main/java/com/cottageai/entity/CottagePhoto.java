package com.cottageai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cottage_photos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CottagePhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cottage_id", nullable = false)
    @ToString.Exclude
    private Cottage cottage;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "is_primary")
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
