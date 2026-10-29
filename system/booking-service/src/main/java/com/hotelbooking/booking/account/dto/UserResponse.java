package com.hotelbooking.booking.account.dto;

import com.hotelbooking.booking.account.persistence.UserRole;

public record UserResponse(Long id, String username, UserRole role) {
}
