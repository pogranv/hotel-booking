package com.hotelbooking.booking.account.dto;

public record TokenResponse(String token, long expiresIn) {
}
