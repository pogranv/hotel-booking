package com.hotelbooking.booking.error;

public class ApiStatusException extends RuntimeException {

	private final int status;

	public ApiStatusException(int status, String message) {
		super(message);
		this.status = status;
	}

	public int getStatus() {
		return this.status;
	}

}
