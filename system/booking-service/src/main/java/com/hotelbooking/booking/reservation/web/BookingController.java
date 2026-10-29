package com.hotelbooking.booking.reservation.web;

import com.hotelbooking.booking.reservation.dto.BookingPageResponse;
import com.hotelbooking.booking.reservation.dto.BookingResponse;
import com.hotelbooking.booking.reservation.dto.CreateBookingRequest;
import com.hotelbooking.booking.reservation.service.BookingQueryService;
import com.hotelbooking.booking.saga.BookingSagaCoordinator;
import com.hotelbooking.booking.saga.SagaOutcome;
import com.hotelbooking.booking.security.CurrentUser;
import com.hotelbooking.common.error.ErrorDto;
import com.hotelbooking.common.error.ErrorDtoFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class BookingController {

	private final BookingSagaCoordinator bookingSagaCoordinator;

	private final BookingQueryService bookingQueryService;

	private final CurrentUser currentUser;

	public BookingController(BookingSagaCoordinator bookingSagaCoordinator, BookingQueryService bookingQueryService,
			CurrentUser currentUser) {
		this.bookingSagaCoordinator = bookingSagaCoordinator;
		this.bookingQueryService = bookingQueryService;
		this.currentUser = currentUser;
	}

	@PostMapping("/booking")
	@PreAuthorize("hasRole('USER')")
	@Operation(summary = "Создать бронирование")
	@ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = BookingResponse.class)))
	@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = BookingResponse.class)))
	@ApiResponse(responseCode = "409",
			content = @Content(schema = @Schema(oneOf = { BookingResponse.class, ErrorDto.class })))
	@ApiResponse(responseCode = "404",
			content = @Content(schema = @Schema(oneOf = { BookingResponse.class, ErrorDto.class })))
	@ApiResponse(responseCode = "503",
			content = @Content(schema = @Schema(oneOf = { BookingResponse.class, ErrorDto.class })))
	@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorDto.class)))
	@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = ErrorDto.class)))
	public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateBookingRequest request,
			HttpServletRequest httpRequest) {
		return respond(httpRequest, this.bookingSagaCoordinator.create(jwt, request));
	}

	@GetMapping("/bookings")
	@PreAuthorize("hasRole('USER')")
	@Operation(summary = "История бронирований")
	@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = BookingPageResponse.class)))
	public BookingPageResponse list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return this.bookingQueryService.page(this.currentUser.requireId(jwt), page, size);
	}

	@GetMapping("/booking/{id}")
	@PreAuthorize("hasRole('USER')")
	@Operation(summary = "Получить бронирование")
	@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = BookingResponse.class)))
	@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorDto.class)))
	@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorDto.class)))
	public BookingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") long id) {
		return this.bookingQueryService.getOwn(this.currentUser.requireId(jwt), id);
	}

	@DeleteMapping("/booking/{id}")
	@PreAuthorize("hasRole('USER')")
	@Operation(summary = "Отменить бронирование")
	@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = BookingResponse.class)))
	@ApiResponse(responseCode = "404",
			content = @Content(schema = @Schema(oneOf = { BookingResponse.class, ErrorDto.class })))
	@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorDto.class)))
	@ApiResponse(responseCode = "503",
			content = @Content(schema = @Schema(oneOf = { BookingResponse.class, ErrorDto.class })))
	@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = ErrorDto.class)))
	public ResponseEntity<?> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") long id,
			HttpServletRequest httpRequest) {
		return respond(httpRequest, this.bookingSagaCoordinator.cancel(jwt, id));
	}

	private static ResponseEntity<?> respond(HttpServletRequest request, SagaOutcome outcome) {
		if (outcome.hasBooking()) {
			return ResponseEntity.status(outcome.status()).body(outcome.booking());
		}
		ErrorDto error = ErrorDtoFactory.of(outcome.status(), outcome.message(), request.getRequestURI());
		return ResponseEntity.status(outcome.status()).contentType(MediaType.APPLICATION_JSON).body(error);
	}

}
