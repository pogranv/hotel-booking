package com.hotelbooking.booking.hotel;

public class HotelClientException extends RuntimeException {

	public HotelClientException(String message) {
		super(message);
	}

	public HotelClientException(Throwable cause) {
		super(cause);
	}

}
