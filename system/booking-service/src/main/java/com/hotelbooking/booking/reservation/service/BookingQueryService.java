package com.hotelbooking.booking.reservation.service;

import java.util.Optional;

import com.hotelbooking.booking.reservation.dto.BookingPageResponse;
import com.hotelbooking.booking.reservation.dto.BookingResponse;
import com.hotelbooking.booking.reservation.persistence.BookingEntity;

public interface BookingQueryService {

	BookingResponse getOwn(long userId, long bookingId);

	BookingPageResponse page(long userId, int page, int size);

	boolean existsByUserId(long userId);

	Optional<BookingResponse> findByRequestId(String requestId);

	Optional<BookingEntity> find(long bookingId);

}
