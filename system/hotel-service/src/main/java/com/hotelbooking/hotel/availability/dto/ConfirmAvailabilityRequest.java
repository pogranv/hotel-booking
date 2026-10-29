package com.hotelbooking.hotel.availability.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfirmAvailabilityRequest {

	@NotBlank
	private String requestId;

	@NotNull
	private Long bookingId;

	@NotNull
	private LocalDate startDate;

	@NotNull
	private LocalDate endDate;

}
