package com.hotelbooking.hotel.room.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoomUpdateRequest {

	@NotBlank
	private String number;

	@NotNull
	@JsonSetter(nulls = Nulls.FAIL)
	private Boolean available;

}
