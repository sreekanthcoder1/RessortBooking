package com.cottageai.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// ─── CREATE / UPDATE REQUEST ─────────────────────────────────
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CottageRequest {

    @NotBlank(message = "Cottage name is required")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "Description is required")
    private String description;

    @NotBlank(message = "Location is required")
    private String location;

    private BigDecimal latitude;
    private BigDecimal longitude;

    @NotNull(message = "Daily price is required")
    @DecimalMin(value = "1.00", message = "Daily price must be at least ₹1")
    private BigDecimal dailyPrice;

    @Min(1) @Max(20)
    private Integer maxGuests;

    @Min(1) @Max(20)
    private Integer bedrooms;

    @Min(1) @Max(10)
    private Integer bathrooms;

    private String amenities;
}
