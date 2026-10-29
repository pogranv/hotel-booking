package com.hotelbooking.booking.account.service;

import java.util.List;

import com.hotelbooking.booking.account.dto.AdminCreateUserRequest;
import com.hotelbooking.booking.account.dto.AdminUpdateUserRequest;
import com.hotelbooking.booking.account.dto.AuthRequest;
import com.hotelbooking.booking.account.dto.RegisterRequest;
import com.hotelbooking.booking.account.dto.TokenResponse;
import com.hotelbooking.booking.account.dto.UserResponse;
import com.hotelbooking.booking.account.mapper.UserMapper;
import com.hotelbooking.booking.account.persistence.UserEntity;
import com.hotelbooking.booking.account.persistence.UserRepository;
import com.hotelbooking.booking.account.persistence.UserRole;
import com.hotelbooking.booking.error.ApiStatusException;
import com.hotelbooking.booking.reservation.service.BookingQueryService;
import com.hotelbooking.booking.security.BookingJwtIssuer;
import com.hotelbooking.common.security.RoleNames;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountServiceImpl implements UserAccountService {

	static final long TOKEN_TTL_SECONDS = 3600;

	private static final String BAD_REQUEST = "Некорректный запрос";

	private static final String ALREADY_EXISTS = "Пользователь уже существует";

	private static final String BAD_CREDENTIALS = "Неверные учетные данные";

	private static final String USER_NOT_FOUND = "Пользователь не найден";

	private static final String HAS_BOOKINGS = "У пользователя есть бронирования";

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final BookingJwtIssuer bookingJwtIssuer;

	private final BookingQueryService bookingQueryService;

	private final UserMapper userMapper;

	public UserAccountServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
			BookingJwtIssuer bookingJwtIssuer, BookingQueryService bookingQueryService, UserMapper userMapper) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.bookingJwtIssuer = bookingJwtIssuer;
		this.bookingQueryService = bookingQueryService;
		this.userMapper = userMapper;
	}

	@Override
	@Transactional
	public TokenResponse register(RegisterRequest request) {
		if (request == null) {
			throw new ApiStatusException(400, BAD_REQUEST);
		}
		requireText(request.username());
		requireText(request.password());
		if (this.userRepository.existsByUsername(request.username())) {
			throw new ApiStatusException(409, ALREADY_EXISTS);
		}
		UserEntity entity = new UserEntity();
		entity.setUsername(request.username());
		entity.setPassword(this.passwordEncoder.encode(request.password()));
		entity.setRole(UserRole.USER);
		try {
			this.userRepository.saveAndFlush(entity);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiStatusException(409, ALREADY_EXISTS);
		}
		String token = this.bookingJwtIssuer.issue(entity.getId(), entity.getRole());
		return new TokenResponse(token, TOKEN_TTL_SECONDS);
	}

	@Override
	@Transactional(readOnly = true)
	public TokenResponse authenticate(AuthRequest request) {
		if (request == null) {
			throw new ApiStatusException(400, BAD_REQUEST);
		}
		requireText(request.username());
		requireText(request.password());
		UserEntity user = this.userRepository.findByUsername(request.username()).orElse(null);
		if (user == null || !this.passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new ApiStatusException(401, BAD_CREDENTIALS);
		}
		String token = this.bookingJwtIssuer.issue(user.getId(), user.getRole());
		return new TokenResponse(token, TOKEN_TTL_SECONDS);
	}

	@Override
	@Transactional
	public UserResponse create(AdminCreateUserRequest request) {
		if (request == null) {
			throw new ApiStatusException(400, BAD_REQUEST);
		}
		requireText(request.username());
		requireText(request.password());
		UserRole role = parseRole(request.role());
		if (this.userRepository.existsByUsername(request.username())) {
			throw new ApiStatusException(409, ALREADY_EXISTS);
		}
		UserEntity entity = new UserEntity();
		entity.setUsername(request.username());
		entity.setPassword(this.passwordEncoder.encode(request.password()));
		entity.setRole(role);
		try {
			this.userRepository.saveAndFlush(entity);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiStatusException(409, ALREADY_EXISTS);
		}
		return this.userMapper.toResponse(entity);
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserResponse> list() {
		return this.userRepository.findAllByOrderByIdAsc().stream().map(this.userMapper::toResponse).toList();
	}

	@Override
	@Transactional
	public UserResponse update(AdminUpdateUserRequest request) {
		if (request == null || request.getId() == null) {
			throw new ApiStatusException(400, BAD_REQUEST);
		}
		UserEntity user = this.userRepository.findById(request.getId())
			.orElseThrow(() -> new ApiStatusException(404, USER_NOT_FOUND));
		if (request.isUsernamePresent()) {
			requireText(request.getUsername());
			if (!request.getUsername().equals(user.getUsername())
					&& this.userRepository.existsByUsername(request.getUsername())) {
				throw new ApiStatusException(409, ALREADY_EXISTS);
			}
			user.setUsername(request.getUsername());
		}
		if (request.isPasswordPresent()) {
			requireText(request.getPassword());
			user.setPassword(this.passwordEncoder.encode(request.getPassword()));
		}
		if (request.isRolePresent()) {
			user.setRole(parseRole(request.getRole()));
		}
		this.userRepository.saveAndFlush(user);
		return this.userMapper.toResponse(user);
	}

	@Override
	@Transactional
	public void delete(long id) {
		UserEntity user = this.userRepository.findById(id)
			.orElseThrow(() -> new ApiStatusException(404, USER_NOT_FOUND));
		if (this.bookingQueryService.existsByUserId(id)) {
			throw new ApiStatusException(409, HAS_BOOKINGS);
		}
		try {
			this.userRepository.delete(user);
			this.userRepository.flush();
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiStatusException(409, HAS_BOOKINGS);
		}
	}

	private static void requireText(String value) {
		if (value == null || value.isBlank() || value.length() > 255) {
			throw new ApiStatusException(400, BAD_REQUEST);
		}
	}

	private static UserRole parseRole(String role) {
		if (!RoleNames.USER.equals(role) && !RoleNames.ADMIN.equals(role)) {
			throw new ApiStatusException(400, BAD_REQUEST);
		}
		return UserRole.valueOf(role);
	}

}
