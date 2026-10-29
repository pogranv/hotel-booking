package com.hotelbooking.booking.saga;

import com.hotelbooking.booking.reservation.dto.CreateBookingRequest;

import org.springframework.security.oauth2.jwt.Jwt;

public interface BookingSagaCoordinator {

	SagaOutcome create(Jwt jwt, CreateBookingRequest request);

	SagaOutcome cancel(Jwt jwt, long bookingId);

}
