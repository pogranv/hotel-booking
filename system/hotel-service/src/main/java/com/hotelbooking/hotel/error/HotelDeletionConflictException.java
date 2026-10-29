package com.hotelbooking.hotel.error;

public class HotelDeletionConflictException extends RuntimeException {

	public HotelDeletionConflictException() {
		super("Нельзя удалить отель, пока есть номера");
	}

}
