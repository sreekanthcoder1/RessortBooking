package com.cottageai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingNotification {
    private String userEmail;
    private String userName;
    private String adminEmail;
    private String bookingRef;
    private String cottageName;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private int totalNights;
    private double totalAmount;
}
