package com.hotelbooking.booking.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.hotelbooking.common.error.ErrorDto;
import com.hotelbooking.common.error.ErrorDtoFactory;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	private static final String BAD_REQUEST = "Некорректный запрос";

	private static final String FORBIDDEN = "Недостаточно прав";

	private static final String INTERNAL = "Internal Server Error";

	@ExceptionHandler(ApiStatusException.class)
	public ResponseEntity<ErrorDto> handleApiStatus(ApiStatusException exception, HttpServletRequest request) {
		return respond(request, exception.getStatus(), exception.getMessage());
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
			MissingServletRequestParameterException.class })
	public ResponseEntity<ErrorDto> handleBadRequest(Exception exception, HttpServletRequest request) {
		return respond(request, 400, BAD_REQUEST);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorDto> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
		return respond(request, 403, FORBIDDEN);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorDto> handleException(Exception exception, HttpServletRequest request) {
		log.error(INTERNAL, exception);
		return respond(request, 500, INTERNAL);
	}

	private static ResponseEntity<ErrorDto> respond(HttpServletRequest request, int status, String message) {
		if (status == 401 || status == 403 || status == 404) {
			log.error(message);
		}
		return ResponseEntity.status(status)
			.contentType(MediaType.APPLICATION_JSON)
			.body(ErrorDtoFactory.of(status, message, request.getRequestURI()));
	}

}
