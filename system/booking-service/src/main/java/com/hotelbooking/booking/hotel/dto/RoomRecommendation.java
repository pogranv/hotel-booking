package com.hotelbooking.booking.hotel.dto;

public record RoomRecommendation(long id, long hotelId, String number, boolean available, int timesBooked) {
}
