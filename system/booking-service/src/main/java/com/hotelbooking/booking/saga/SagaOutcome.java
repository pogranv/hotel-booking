package com.hotelbooking.booking.saga;

import com.hotelbooking.booking.reservation.dto.BookingResponse;

public record SagaOutcome(int status, BookingResponse booking, String message) {

	public static SagaOutcome ofBooking(int status, BookingResponse booking) {
		return new SagaOutcome(status, booking, null);
	}

	public static SagaOutcome ofError(int status, String message) {
		return new SagaOutcome(status, null, message);
	}

	public boolean hasBooking() {
		return this.booking != null;
	}

}
