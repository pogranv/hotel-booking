package com.hotelbooking.hotel.availability;

import java.time.Instant;
import java.util.Optional;

import com.hotelbooking.common.trace.TraceConstants;
import com.hotelbooking.hotel.error.AvailabilityConflictException;
import com.hotelbooking.hotel.error.RoomNotFoundException;
import com.hotelbooking.hotel.room.Room;
import com.hotelbooking.hotel.room.RoomRepository;
import com.hotelbooking.hotel.availability.dto.ConfirmAvailabilityRequest;
import com.hotelbooking.hotel.availability.dto.ReleaseRoomRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RoomAvailabilityServiceImpl implements RoomAvailabilityService {

	private static final Logger log = LoggerFactory.getLogger(RoomAvailabilityServiceImpl.class);

	private static final String EVENT_LOCK = "hotel.lock";

	private static final String EVENT_CONFLICT = "hotel.lock.conflict";

	private static final String EVENT_RELEASE = "hotel.release";

	private final RoomRepository roomRepository;

	private final RoomLockRepository roomLockRepository;

	private final TransactionTemplate transactionTemplate;

	public RoomAvailabilityServiceImpl(RoomRepository roomRepository, RoomLockRepository roomLockRepository,
			PlatformTransactionManager transactionManager) {
		this.roomRepository = roomRepository;
		this.roomLockRepository = roomLockRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
	}

	@Override
	public void confirm(Long roomId, ConfirmAvailabilityRequest request) {
		if (!request.getStartDate().isBefore(request.getEndDate())) {
			throw new IllegalArgumentException("startDate must be before endDate");
		}
		this.transactionTemplate.executeWithoutResult(status -> confirmLocked(roomId, request));
	}

	@Override
	public void release(Long roomId, ReleaseRoomRequest request) {
		this.transactionTemplate.executeWithoutResult(status -> releaseLocked(roomId, request));
	}

	private void confirmLocked(Long roomId, ConfirmAvailabilityRequest request) {
		try {
			Optional<Room> locked = this.roomRepository.findWithWriteLock(roomId);
			putIds(request.getBookingId(), request.getRequestId());
			if (locked.isEmpty()) {
				log.error("Номер не найден");
				throw new RoomNotFoundException();
			}
			Room room = locked.get();
			Optional<RoomLock> byRequest = this.roomLockRepository.findByRequestId(request.getRequestId());
			if (byRequest.isPresent()) {
				if (byRequest.get().getStatus() == RoomLock.Status.ACTIVE) {
					MDC.put(TraceConstants.MDC_EVENT, EVENT_LOCK);
					log.info("Повторное подтверждение");
					return;
				}
				conflict("Повтор requestId после снятия блокировки");
			}
			if (this.roomLockRepository.findByBookingId(request.getBookingId()).isPresent()) {
				conflict("bookingId уже использован");
			}
			if (!room.isAvailable()) {
				conflict("Номер недоступен");
			}
			if (this.roomLockRepository.findActiveOverlap(roomId, request.getStartDate(), request.getEndDate())
				.isPresent()) {
				conflict("Интервал пересекается");
			}
			RoomLock created = new RoomLock();
			created.setRoom(room);
			created.setBookingId(request.getBookingId());
			created.setRequestId(request.getRequestId());
			created.setStartDate(request.getStartDate());
			created.setEndDate(request.getEndDate());
			created.setStatus(RoomLock.Status.ACTIVE);
			created.setCreatedAt(Instant.now());
			room.setTimesBooked(room.getTimesBooked() + 1);
			try {
				this.roomLockRepository.saveAndFlush(created);
				this.roomRepository.saveAndFlush(room);
			}
			catch (DataIntegrityViolationException ex) {
				conflict("Нарушение уникальности блокировки", ex);
			}
			MDC.put(TraceConstants.MDC_EVENT, EVENT_LOCK);
			log.info("Блокировка создана");
		}
		finally {
			clearIds();
		}
	}

	private void releaseLocked(Long roomId, ReleaseRoomRequest request) {
		try {
			this.roomRepository.findWithWriteLock(roomId);
			putIds(request.getBookingId(), request.getRequestId());
			Optional<RoomLock> lock = this.roomLockRepository.findByRoomIdAndBookingIdAndStatus(roomId,
					request.getBookingId(), RoomLock.Status.ACTIVE);
			if (lock.isPresent()) {
				lock.get().setStatus(RoomLock.Status.RELEASED);
				this.roomLockRepository.saveAndFlush(lock.get());
				MDC.put(TraceConstants.MDC_EVENT, EVENT_RELEASE);
				log.info("Блокировка снята");
			}
			else {
				log.info("Активная блокировка не найдена");
			}
		}
		finally {
			clearIds();
		}
	}

	private void putIds(Long bookingId, String requestId) {
		MDC.put(TraceConstants.MDC_BOOKING_ID, bookingId.toString());
		MDC.put(TraceConstants.MDC_REQUEST_ID, requestId);
	}

	private void conflict(String message) {
		conflict(message, null);
	}

	private void conflict(String message, Throwable cause) {
		MDC.put(TraceConstants.MDC_EVENT, EVENT_CONFLICT);
		log.error(message, cause);
		throw new AvailabilityConflictException();
	}

	private void clearIds() {
		MDC.remove(TraceConstants.MDC_BOOKING_ID);
		MDC.remove(TraceConstants.MDC_REQUEST_ID);
		MDC.remove(TraceConstants.MDC_EVENT);
	}

}
