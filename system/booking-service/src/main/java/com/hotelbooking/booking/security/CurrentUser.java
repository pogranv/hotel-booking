package com.hotelbooking.booking.security;

import com.hotelbooking.booking.account.persistence.UserRepository;
import com.hotelbooking.booking.error.ApiStatusException;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

	private static final String INVALID_TOKEN = "Недействительный токен";

	private final UserRepository userRepository;

	public CurrentUser(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public long requireId(Jwt jwt) {
		if (jwt == null) {
			throw new ApiStatusException(401, INVALID_TOKEN);
		}
		String subject = jwt.getSubject();
		if (subject == null || !subject.matches("[0-9]+")) {
			throw new ApiStatusException(401, INVALID_TOKEN);
		}
		long id = Long.parseLong(subject);
		if (!this.userRepository.existsById(id)) {
			throw new ApiStatusException(401, INVALID_TOKEN);
		}
		return id;
	}

}
