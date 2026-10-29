package com.hotelbooking.booking.reservation.dto;

import java.time.LocalDate;

public record CreateBookingRequest(String requestId, Boolean autoSelect, Long roomId, LocalDate startDate,
		LocalDate endDate) {
}
