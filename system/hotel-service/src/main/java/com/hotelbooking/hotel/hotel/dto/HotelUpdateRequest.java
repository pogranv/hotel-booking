package com.hotelbooking.hotel.hotel.dto;

import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HotelUpdateRequest {

	@NotBlank
	private String name;

	@NotBlank
	private String address;

}
