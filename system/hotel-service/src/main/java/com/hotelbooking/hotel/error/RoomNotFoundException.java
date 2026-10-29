package com.hotelbooking.hotel.error;

public class RoomNotFoundException extends RuntimeException {

	public RoomNotFoundException() {
		super("Номер не найден");
	}

}
