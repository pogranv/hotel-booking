package com.hotelbooking.hotel.error;

import com.hotelbooking.common.error.ErrorDto;
import com.hotelbooking.common.error.ErrorDtoFactory;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class HotelRestExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(HotelRestExceptionHandler.class);

	@ExceptionHandler({ HotelNotFoundException.class, RoomNotFoundException.class })
	public ResponseEntity<ErrorDto> handleNotFound(RuntimeException ex, HttpServletRequest request) {
		log.error(ex.getMessage(), ex);
		return body(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler({ DuplicateRoomNumberException.class, HotelDeletionConflictException.class,
			RoomDeletionConflictException.class, AvailabilityConflictException.class })
	public ResponseEntity<ErrorDto> handleConflict(RuntimeException ex, HttpServletRequest request) {
		log.error(ex.getMessage(), ex);
		return body(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler({ MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
			HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
			IllegalArgumentException.class })
	public ResponseEntity<ErrorDto> handleBadRequest(Exception ex, HttpServletRequest request) {
		log.error("Некорректный запрос", ex);
		return body(HttpStatus.BAD_REQUEST, "Некорректный запрос", request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorDto> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		log.error("Недостаточно прав", ex);
		return body(HttpStatus.FORBIDDEN, "Недостаточно прав", request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorDto> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Внутренняя ошибка", ex);
		return body(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка", request);
	}

	private ResponseEntity<ErrorDto> body(HttpStatus status, String message, HttpServletRequest request) {
		ErrorDto dto = ErrorDtoFactory.of(status.value(), message, request.getRequestURI());
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(dto);
	}

}
