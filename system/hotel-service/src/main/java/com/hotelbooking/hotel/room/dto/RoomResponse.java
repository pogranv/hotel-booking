package com.hotelbooking.hotel.room.dto;

public record RoomResponse(Long id, Long hotelId, String number, boolean available, int timesBooked) {
}
