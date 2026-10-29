package com.hotelbooking.booking.reservation.dto;

import java.util.List;

public record BookingPageResponse(List<BookingResponse> content, int page, int size, long totalElements) {
}
