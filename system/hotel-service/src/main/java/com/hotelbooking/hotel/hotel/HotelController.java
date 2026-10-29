package com.hotelbooking.hotel.hotel;

import java.util.List;

import com.hotelbooking.common.security.RoleNames;
import com.hotelbooking.hotel.hotel.dto.HotelCreateRequest;
import com.hotelbooking.hotel.hotel.dto.HotelResponse;
import com.hotelbooking.hotel.hotel.dto.HotelUpdateRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hotels")
public class HotelController {

	private final HotelService hotelService;

	public HotelController(HotelService hotelService) {
		this.hotelService = hotelService;
	}

	@PostMapping
	@PreAuthorize("hasRole('" + RoleNames.ADMIN + "')")
	public ResponseEntity<HotelResponse> create(@Valid @RequestBody HotelCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(this.hotelService.create(request));
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('" + RoleNames.USER + "','" + RoleNames.ADMIN + "')")
	public List<HotelResponse> findAll() {
		return this.hotelService.findAll();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('" + RoleNames.USER + "','" + RoleNames.ADMIN + "')")
	public HotelResponse findById(@PathVariable Long id) {
		return this.hotelService.findById(id);
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasRole('" + RoleNames.ADMIN + "')")
	public HotelResponse update(@PathVariable Long id, @Valid @RequestBody HotelUpdateRequest request) {
		return this.hotelService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('" + RoleNames.ADMIN + "')")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		this.hotelService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
