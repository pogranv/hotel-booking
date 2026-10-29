package com.hotelbooking.hotel.error;

public class RoomDeletionConflictException extends RuntimeException {

	public RoomDeletionConflictException() {
		super("Нельзя удалить номер с активной блокировкой");
	}

}
