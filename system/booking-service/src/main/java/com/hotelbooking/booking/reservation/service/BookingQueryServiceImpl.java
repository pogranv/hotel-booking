package com.hotelbooking.booking.reservation.service;

import java.util.Optional;

import com.hotelbooking.booking.error.ApiStatusException;
import com.hotelbooking.booking.reservation.dto.BookingPageResponse;
import com.hotelbooking.booking.reservation.dto.BookingResponse;
import com.hotelbooking.booking.reservation.mapper.BookingMapper;
import com.hotelbooking.booking.reservation.persistence.BookingEntity;
import com.hotelbooking.booking.reservation.persistence.BookingRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingQueryServiceImpl implements BookingQueryService {

	private static final String BAD_REQUEST = "Некорректный запрос";

	private static final String NOT_FOUND = "Бронирование не найдено";

	private static final String FORBIDDEN = "Недостаточно прав";

	private final BookingRepository bookingRepository;

	private final BookingMapper bookingMapper;

	public BookingQueryServiceImpl(BookingRepository bookingRepository, BookingMapper bookingMapper) {
		this.bookingRepository = bookingRepository;
		this.bookingMapper = bookingMapper;
	}

	@Override
	@Transactional(readOnly = true)
	public BookingResponse getOwn(long userId, long bookingId) {
		BookingEntity entity = this.bookingRepository.findById(bookingId)
			.orElseThrow(() -> new ApiStatusException(404, NOT_FOUND));
		touchUser(entity);
		if (!entity.getUser().getId().equals(userId)) {
			throw new ApiStatusException(403, FORBIDDEN);
		}
		return this.bookingMapper.toResponse(entity);
	}

	@Override
	@Transactional(readOnly = true)
	public BookingPageResponse page(long userId, int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new ApiStatusException(400, BAD_REQUEST);
		}
		Page<BookingEntity> result = this.bookingRepository.findByUserId(userId,
				PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
		return new BookingPageResponse(result.getContent().stream().map(entity -> {
			touchUser(entity);
			return this.bookingMapper.toResponse(entity);
		}).toList(), page, size, result.getTotalElements());
	}

	@Override
	@Transactional(readOnly = true)
	public boolean existsByUserId(long userId) {
		return this.bookingRepository.existsByUserId(userId);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<BookingResponse> findByRequestId(String requestId) {
		return this.bookingRepository.findByRequestId(requestId).map(entity -> {
			touchUser(entity);
			return this.bookingMapper.toResponse(entity);
		});
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<BookingEntity> find(long bookingId) {
		return this.bookingRepository.findById(bookingId).map(entity -> {
			touchUser(entity);
			return entity;
		});
	}

	private static void touchUser(BookingEntity entity) {
		entity.getUser().getId();
	}

}
