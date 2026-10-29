package com.hotelbooking.hotel.hotel.dto;

import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HotelCreateRequest {

	@NotBlank
	private String name;

	@NotBlank
	private String address;

}
