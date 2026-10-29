package com.hotelbooking.booking.account.service;

import java.util.List;

import com.hotelbooking.booking.account.dto.AdminCreateUserRequest;
import com.hotelbooking.booking.account.dto.AdminUpdateUserRequest;
import com.hotelbooking.booking.account.dto.AuthRequest;
import com.hotelbooking.booking.account.dto.RegisterRequest;
import com.hotelbooking.booking.account.dto.TokenResponse;
import com.hotelbooking.booking.account.dto.UserResponse;

public interface UserAccountService {

	TokenResponse register(RegisterRequest request);

	TokenResponse authenticate(AuthRequest request);

	UserResponse create(AdminCreateUserRequest request);

	List<UserResponse> list();

	UserResponse update(AdminUpdateUserRequest request);

	void delete(long id);

}
