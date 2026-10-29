package com.hotelbooking.booking.reservation.service;

import java.time.LocalDate;

import com.hotelbooking.booking.reservation.persistence.BookingEntity;

public interface BookingCommandService {

	BookingEntity insertPending(long userId, long roomId, LocalDate startDate, LocalDate endDate, String requestId);

	BookingEntity markConfirmed(long bookingId);

	BookingEntity markCancelled(long bookingId);

}
