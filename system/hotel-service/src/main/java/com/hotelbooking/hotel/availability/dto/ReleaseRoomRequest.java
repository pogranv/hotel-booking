package com.hotelbooking.hotel.availability.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReleaseRoomRequest {

	@NotBlank
	private String requestId;

	@NotNull
	private Long bookingId;

}
