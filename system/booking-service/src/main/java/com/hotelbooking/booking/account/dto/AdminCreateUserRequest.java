package com.hotelbooking.booking.account.dto;

public record AdminCreateUserRequest(String username, String password, String role) {
}
