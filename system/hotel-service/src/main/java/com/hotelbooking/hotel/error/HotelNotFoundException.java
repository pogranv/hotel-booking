package com.hotelbooking.hotel.error;

public class HotelNotFoundException extends RuntimeException {

	public HotelNotFoundException() {
		super("Отель не найден");
	}

}
