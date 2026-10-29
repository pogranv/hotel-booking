package com.hotelbooking.hotel.error;

public class DuplicateRoomNumberException extends RuntimeException {

	public DuplicateRoomNumberException() {
		super("Номер уже есть в этом отеле");
	}

}
