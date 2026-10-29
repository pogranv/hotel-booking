package com.hotelbooking.booking.hotel.dto;

import java.time.LocalDate;

public record ConfirmAvailabilityCommand(String requestId, long bookingId, LocalDate startDate, LocalDate endDate) {
}
