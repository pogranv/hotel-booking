package com.hotelbooking.booking.account.web;

import java.util.List;

import com.hotelbooking.booking.account.dto.AdminCreateUserRequest;
import com.hotelbooking.booking.account.dto.AdminUpdateUserRequest;
import com.hotelbooking.booking.account.dto.AuthRequest;
import com.hotelbooking.booking.account.dto.RegisterRequest;
import com.hotelbooking.booking.account.dto.TokenResponse;
import com.hotelbooking.booking.account.dto.UserResponse;
import com.hotelbooking.booking.account.service.UserAccountService;
import com.hotelbooking.common.error.ErrorDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

	private final UserAccountService userAccountService;

	public UserController(UserAccountService userAccountService) {
		this.userAccountService = userAccountService;
	}

	@PostMapping("/user/register")
	@Operation(summary = "Зарегистрировать пользователя")
	@ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = TokenResponse.class)))
	@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorDto.class)))
	@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorDto.class)))
	public ResponseEntity<TokenResponse> register(@RequestBody RegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(this.userAccountService.register(request));
	}

	@PostMapping("/user/auth")
	@Operation(summary = "Авторизовать пользователя")
	@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = TokenResponse.class)))
	@ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorDto.class)))
	public TokenResponse auth(@RequestBody AuthRequest request) {
		return this.userAccountService.authenticate(request);
	}

	@PostMapping("/user")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Создать пользователя")
	@ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = UserResponse.class)))
	@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorDto.class)))
	@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorDto.class)))
	public ResponseEntity<UserResponse> create(@RequestBody AdminCreateUserRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(this.userAccountService.create(request));
	}

	@GetMapping("/user")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Список пользователей")
	@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = UserResponse.class)))
	public List<UserResponse> list() {
		return this.userAccountService.list();
	}

	@PatchMapping("/user")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Обновить пользователя")
	@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = UserResponse.class)))
	@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorDto.class)))
	@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorDto.class)))
	public UserResponse update(@RequestBody AdminUpdateUserRequest request) {
		return this.userAccountService.update(request);
	}

	@DeleteMapping("/user")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Удалить пользователя")
	@ApiResponse(responseCode = "204")
	@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorDto.class)))
	@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(implementation = ErrorDto.class)))
	public void delete(@RequestParam("id") long id) {
		this.userAccountService.delete(id);
	}

}
