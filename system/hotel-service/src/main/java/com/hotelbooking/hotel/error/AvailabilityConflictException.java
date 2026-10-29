package com.hotelbooking.hotel.error;

public class AvailabilityConflictException extends RuntimeException {

	public AvailabilityConflictException() {
		super("Подтверждение конфликтует с текущей блокировкой");
	}

}
