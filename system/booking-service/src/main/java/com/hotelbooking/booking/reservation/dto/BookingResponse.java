package com.hotelbooking.booking.reservation.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.hotelbooking.booking.reservation.persistence.BookingStatus;

public record BookingResponse(Long id, Long userId, Long roomId, LocalDate startDate, LocalDate endDate,
		BookingStatus status, Instant createdAt) {
}
