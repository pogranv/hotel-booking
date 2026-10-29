package com.hotelbooking.booking.saga;

import java.util.List;
import java.util.Optional;

import com.hotelbooking.booking.error.ApiStatusException;
import com.hotelbooking.booking.hotel.HotelClient;
import com.hotelbooking.booking.hotel.HotelClientException;
import com.hotelbooking.booking.hotel.HotelConfirmStatus;
import com.hotelbooking.booking.hotel.HotelRejectedStatusException;
import com.hotelbooking.booking.hotel.dto.ConfirmAvailabilityCommand;
import com.hotelbooking.booking.hotel.dto.ReleaseRoomCommand;
import com.hotelbooking.booking.hotel.dto.RoomRecommendation;
import com.hotelbooking.booking.reservation.dto.BookingResponse;
import com.hotelbooking.booking.reservation.dto.CreateBookingRequest;
import com.hotelbooking.booking.reservation.mapper.BookingMapper;
import com.hotelbooking.booking.reservation.persistence.BookingEntity;
import com.hotelbooking.booking.reservation.persistence.BookingStatus;
import com.hotelbooking.booking.reservation.service.BookingCommandService;
import com.hotelbooking.booking.reservation.service.BookingQueryService;
import com.hotelbooking.booking.security.CurrentUser;
import com.hotelbooking.common.trace.TraceConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class BookingSagaCoordinatorImpl implements BookingSagaCoordinator {

	private static final Logger log = LoggerFactory.getLogger(BookingSagaCoordinatorImpl.class);

	private static final String BAD_REQUEST = "Некорректный запрос";

	private static final String DATE_MESSAGE = "Дата выезда должна быть позже даты заезда";

	private static final String NO_ROOMS = "Свободных номеров нет";

	private static final String HOTEL_DOWN = "Сервис отелей недоступен";

	private static final String INTERNAL = "Internal Server Error";

	private static final String NOT_FOUND = "Бронирование не найдено";

	private static final String FORBIDDEN = "Недостаточно прав";

	private static final String COMPENSATE = "booking.compensate";

	private final BookingCommandService bookingCommandService;

	private final BookingQueryService bookingQueryService;

	private final HotelClient hotelClient;

	private final BookingMapper bookingMapper;

	private final CurrentUser currentUser;

	public BookingSagaCoordinatorImpl(BookingCommandService bookingCommandService,
			BookingQueryService bookingQueryService, HotelClient hotelClient, BookingMapper bookingMapper,
			CurrentUser currentUser) {
		this.bookingCommandService = bookingCommandService;
		this.bookingQueryService = bookingQueryService;
		this.hotelClient = hotelClient;
		this.bookingMapper = bookingMapper;
		this.currentUser = currentUser;
	}

	@Override
	public SagaOutcome create(Jwt jwt, CreateBookingRequest request) {
		try {
			long userId = this.currentUser.requireId(jwt);
			rememberRequestId(request);
			Optional<BookingResponse> existing = findReplay(request);
			if (existing.isPresent()) {
				return SagaOutcome.ofBooking(200, existing.get());
			}
			validateCreate(request);
			long roomId = resolveRoom(request);
			BookingEntity pending;
			try {
				pending = this.bookingCommandService.insertPending(userId, roomId, request.startDate(),
						request.endDate(), request.requestId());
			}
			catch (DataIntegrityViolationException ex) {
				return replayAfterConflict(request.requestId());
			}
			return confirmPending(pending, roomId, request);
		}
		finally {
			clearSagaMdc();
		}
	}

	@Override
	public SagaOutcome cancel(Jwt jwt, long bookingId) {
		try {
			long userId = this.currentUser.requireId(jwt);
			Optional<BookingEntity> found = this.bookingQueryService.find(bookingId);
			if (found.isEmpty()) {
				log.error(NOT_FOUND);
				return SagaOutcome.ofError(404, NOT_FOUND);
			}
			BookingEntity entity = found.get();
			MDC.put(TraceConstants.MDC_BOOKING_ID, Long.toString(entity.getId()));
			MDC.put(TraceConstants.MDC_REQUEST_ID, entity.getRequestId());
			if (!entity.getUser().getId().equals(userId)) {
				log.error(FORBIDDEN);
				return SagaOutcome.ofError(403, FORBIDDEN);
			}
			if (entity.getStatus() == BookingStatus.CANCELLED) {
				return SagaOutcome.ofBooking(200, this.bookingMapper.toResponse(entity));
			}
			return releaseAndCancel(entity, 200);
		}
		finally {
			clearSagaMdc();
		}
	}

	private long resolveRoom(CreateBookingRequest request) {
		if (!request.autoSelect()) {
			return request.roomId();
		}
		List<RoomRecommendation> rooms;
		try {
			rooms = this.hotelClient.recommend();
		}
		catch (HotelClientException ex) {
			throw new ApiStatusException(503, HOTEL_DOWN);
		}
		catch (HotelRejectedStatusException ex) {
			throw new ApiStatusException(500, INTERNAL);
		}
		if (rooms == null || rooms.isEmpty()) {
			throw new ApiStatusException(409, NO_ROOMS);
		}
		return rooms.get(0).id();
	}

	private SagaOutcome confirmPending(BookingEntity pending, long roomId, CreateBookingRequest request) {
		ConfirmAvailabilityCommand command = new ConfirmAvailabilityCommand(request.requestId(), pending.getId(),
				request.startDate(), request.endDate());
		try {
			HotelConfirmStatus result = this.hotelClient.confirm(roomId, command);
			if (result == HotelConfirmStatus.OK) {
				return mapStatusChange(this.bookingCommandService.markConfirmed(pending.getId()), 201);
			}
			if (result == HotelConfirmStatus.CONFLICT || result == HotelConfirmStatus.NOT_FOUND) {
				int status = result == HotelConfirmStatus.CONFLICT ? 409 : 404;
				return mapStatusChange(this.bookingCommandService.markCancelled(pending.getId()), status);
			}
			return SagaOutcome.ofError(500, INTERNAL);
		}
		catch (HotelRejectedStatusException ex) {
			return SagaOutcome.ofError(500, INTERNAL);
		}
		catch (HotelClientException ex) {
			return compensate(pending, roomId, 503);
		}
		catch (RuntimeException ex) {
			log.error(INTERNAL, ex);
			return SagaOutcome.ofError(500, INTERNAL);
		}
	}

	private SagaOutcome releaseAndCancel(BookingEntity entity, int successStatus) {
		logCompensate(entity.getId(), entity.getRequestId());
		try {
			this.hotelClient.release(entity.getRoomId(), new ReleaseRoomCommand(entity.getRequestId(), entity.getId()));
		}
		catch (HotelClientException ex) {
			return SagaOutcome.ofBooking(503, this.bookingMapper.toResponse(entity));
		}
		catch (HotelRejectedStatusException ex) {
			return SagaOutcome.ofError(500, INTERNAL);
		}
		try {
			BookingEntity cancelled = this.bookingCommandService.markCancelled(entity.getId());
			return SagaOutcome.ofBooking(successStatus, this.bookingMapper.toResponse(cancelled));
		}
		catch (RuntimeException ex) {
			log.error(INTERNAL, ex);
			return SagaOutcome.ofError(500, INTERNAL);
		}
	}

	private SagaOutcome compensate(BookingEntity pending, long roomId, int statusAfterRelease) {
		logCompensate(pending.getId(), pending.getRequestId());
		try {
			this.hotelClient.release(roomId, new ReleaseRoomCommand(pending.getRequestId(), pending.getId()));
		}
		catch (HotelClientException ex) {
			return SagaOutcome.ofBooking(503, this.bookingMapper.toResponse(pending));
		}
		catch (HotelRejectedStatusException ex) {
			return SagaOutcome.ofError(500, INTERNAL);
		}
		try {
			BookingEntity cancelled = this.bookingCommandService.markCancelled(pending.getId());
			return SagaOutcome.ofBooking(statusAfterRelease, this.bookingMapper.toResponse(cancelled));
		}
		catch (RuntimeException ex) {
			log.error(INTERNAL, ex);
			return SagaOutcome.ofError(500, INTERNAL);
		}
	}

	private SagaOutcome mapStatusChange(BookingEntity entity, int status) {
		return SagaOutcome.ofBooking(status, this.bookingMapper.toResponse(entity));
	}

	private SagaOutcome replayAfterConflict(String requestId) {
		Optional<BookingResponse> current = this.bookingQueryService.findByRequestId(requestId);
		if (current.isPresent()) {
			return SagaOutcome.ofBooking(200, current.get());
		}
		return SagaOutcome.ofError(500, INTERNAL);
	}

	private Optional<BookingResponse> findReplay(CreateBookingRequest request) {
		if (request == null || request.requestId() == null || request.requestId().isBlank()) {
			return Optional.empty();
		}
		return this.bookingQueryService.findByRequestId(request.requestId());
	}

	private void validateCreate(CreateBookingRequest request) {
		if (request == null) {
			throw new ApiStatusException(400, BAD_REQUEST);
		}
		requireText(request.requestId());
		if (request.autoSelect() == null || request.startDate() == null || request.endDate() == null) {
			throw new ApiStatusException(400, BAD_REQUEST);
		}
		if (!request.startDate().isBefore(request.endDate())) {
			throw new ApiStatusException(400, DATE_MESSAGE);
		}
		if (!request.autoSelect() && request.roomId() == null) {
			throw new ApiStatusException(400, BAD_REQUEST);
		}
	}

	private static void requireText(String value) {
		if (value == null || value.isBlank() || value.length() > 255) {
			throw new ApiStatusException(400, BAD_REQUEST);
		}
	}

	private static void rememberRequestId(CreateBookingRequest request) {
		if (request != null && request.requestId() != null && !request.requestId().isBlank()) {
			MDC.put(TraceConstants.MDC_REQUEST_ID, request.requestId());
		}
	}

	private static void logCompensate(long bookingId, String requestId) {
		MDC.put(TraceConstants.MDC_BOOKING_ID, Long.toString(bookingId));
		MDC.put(TraceConstants.MDC_REQUEST_ID, requestId);
		MDC.put(TraceConstants.MDC_EVENT, COMPENSATE);
		log.info(COMPENSATE);
	}

	private static void clearSagaMdc() {
		MDC.remove(TraceConstants.MDC_BOOKING_ID);
		MDC.remove(TraceConstants.MDC_REQUEST_ID);
		MDC.remove(TraceConstants.MDC_EVENT);
	}

}
