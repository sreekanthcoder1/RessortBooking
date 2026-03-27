package com.cottageai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cottage_availability",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cottage_id", "date"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CottageAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cottage_id", nullable = false)
    @ToString.Exclude
    private Cottage cottage;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "is_blocked")
    @Builder.Default
    private Boolean isBlocked = false;

    @Column(name = "custom_price", precision = 10, scale = 2)
    private BigDecimal customPrice;
}
