package com.hotelbooking.booking.account.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

public class AdminUpdateUserRequest {

	private Long id;

	private String username;

	private boolean usernamePresent;

	private String password;

	private boolean passwordPresent;

	private String role;

	private boolean rolePresent;

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUsername() {
		return this.username;
	}

	@JsonSetter("username")
	public void setUsername(String username) {
		this.usernamePresent = true;
		this.username = username;
	}

	@JsonIgnore
	public boolean isUsernamePresent() {
		return this.usernamePresent;
	}

	public String getPassword() {
		return this.password;
	}

	@JsonSetter("password")
	public void setPassword(String password) {
		this.passwordPresent = true;
		this.password = password;
	}

	@JsonIgnore
	public boolean isPasswordPresent() {
		return this.passwordPresent;
	}

	public String getRole() {
		return this.role;
	}

	@JsonSetter("role")
	public void setRole(String role) {
		this.rolePresent = true;
		this.role = role;
	}

	@JsonIgnore
	public boolean isRolePresent() {
		return this.rolePresent;
	}

}
