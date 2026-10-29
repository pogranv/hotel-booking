package com.hotelbooking.booking.reservation.service;

import java.time.Instant;
import java.time.LocalDate;

import com.hotelbooking.booking.account.persistence.UserEntity;
import com.hotelbooking.booking.account.persistence.UserRepository;
import com.hotelbooking.booking.error.ApiStatusException;
import com.hotelbooking.booking.reservation.persistence.BookingEntity;
import com.hotelbooking.booking.reservation.persistence.BookingRepository;
import com.hotelbooking.booking.reservation.persistence.BookingStatus;
import com.hotelbooking.common.trace.TraceConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingCommandServiceImpl implements BookingCommandService {

	private static final Logger log = LoggerFactory.getLogger(BookingCommandServiceImpl.class);

	private final BookingRepository bookingRepository;

	private final UserRepository userRepository;

	public BookingCommandServiceImpl(BookingRepository bookingRepository, UserRepository userRepository) {
		this.bookingRepository = bookingRepository;
		this.userRepository = userRepository;
	}

	@Override
	@Transactional
	public BookingEntity insertPending(long userId, long roomId, LocalDate startDate, LocalDate endDate,
			String requestId) {
		UserEntity user = this.userRepository.findById(userId)
			.orElseThrow(() -> new ApiStatusException(401, "Недействительный токен"));
		BookingEntity entity = new BookingEntity();
		entity.setUser(user);
		entity.setRoomId(roomId);
		entity.setStartDate(startDate);
		entity.setEndDate(endDate);
		entity.setStatus(BookingStatus.PENDING);
		entity.setCreatedAt(Instant.now());
		entity.setRequestId(requestId);
		this.bookingRepository.saveAndFlush(entity);
		entity.getUser().getId();
		logSaved(entity, "booking.pending");
		return entity;
	}

	@Override
	@Transactional
	public BookingEntity markConfirmed(long bookingId) {
		BookingEntity entity = this.bookingRepository.findById(bookingId)
			.orElseThrow(() -> new IllegalStateException("booking missing"));
		entity.setStatus(BookingStatus.CONFIRMED);
		this.bookingRepository.saveAndFlush(entity);
		entity.getUser().getId();
		logSaved(entity, "booking.confirmed");
		return entity;
	}

	@Override
	@Transactional
	public BookingEntity markCancelled(long bookingId) {
		BookingEntity entity = this.bookingRepository.findById(bookingId)
			.orElseThrow(() -> new IllegalStateException("booking missing"));
		entity.setStatus(BookingStatus.CANCELLED);
		this.bookingRepository.saveAndFlush(entity);
		entity.getUser().getId();
		logSaved(entity, "booking.cancelled");
		return entity;
	}

	private static void logSaved(BookingEntity entity, String event) {
		MDC.put(TraceConstants.MDC_BOOKING_ID, Long.toString(entity.getId()));
		MDC.put(TraceConstants.MDC_REQUEST_ID, entity.getRequestId());
		MDC.put(TraceConstants.MDC_EVENT, event);
		log.info(event);
	}

}
