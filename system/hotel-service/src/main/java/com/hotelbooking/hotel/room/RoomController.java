package com.hotelbooking.hotel.room;

import java.util.List;

import com.hotelbooking.common.security.RoleNames;
import com.hotelbooking.hotel.availability.RoomAvailabilityService;
import com.hotelbooking.hotel.availability.dto.ConfirmAvailabilityRequest;
import com.hotelbooking.hotel.availability.dto.ReleaseRoomRequest;
import com.hotelbooking.hotel.recommendation.RoomRecommendationService;
import com.hotelbooking.hotel.room.dto.RoomCreateRequest;
import com.hotelbooking.hotel.room.dto.RoomResponse;
import com.hotelbooking.hotel.room.dto.RoomUpdateRequest;
import com.hotelbooking.hotel.statistics.RoomStatisticsService;
import com.hotelbooking.hotel.statistics.dto.RoomStatisticsItem;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

	private final RoomService roomService;

	private final RoomRecommendationService roomRecommendationService;

	private final RoomStatisticsService roomStatisticsService;

	private final RoomAvailabilityService roomAvailabilityService;

	public RoomController(RoomService roomService, RoomRecommendationService roomRecommendationService,
			RoomStatisticsService roomStatisticsService, RoomAvailabilityService roomAvailabilityService) {
		this.roomService = roomService;
		this.roomRecommendationService = roomRecommendationService;
		this.roomStatisticsService = roomStatisticsService;
		this.roomAvailabilityService = roomAvailabilityService;
	}

	@GetMapping("/recommend")
	@PreAuthorize("hasAnyRole('" + RoleNames.USER + "','" + RoleNames.ADMIN + "')")
	public List<RoomResponse> recommend() {
		return this.roomRecommendationService.recommend();
	}

	@GetMapping("/statistics")
	@PreAuthorize("hasRole('" + RoleNames.ADMIN + "')")
	public List<RoomStatisticsItem> statistics(@RequestParam(required = false) Long hotelId) {
		return this.roomStatisticsService.findStatistics(hotelId);
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('" + RoleNames.USER + "','" + RoleNames.ADMIN + "')")
	public List<RoomResponse> findAvailable(@RequestParam(required = false) Long hotelId) {
		return this.roomService.findAvailable(hotelId);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('" + RoleNames.USER + "','" + RoleNames.ADMIN + "')")
	public RoomResponse findById(@PathVariable Long id) {
		return this.roomService.findById(id);
	}

	@PostMapping
	@PreAuthorize("hasRole('" + RoleNames.ADMIN + "')")
	public ResponseEntity<RoomResponse> create(@Valid @RequestBody RoomCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(this.roomService.create(request));
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasRole('" + RoleNames.ADMIN + "')")
	public RoomResponse update(@PathVariable Long id, @Valid @RequestBody RoomUpdateRequest request) {
		return this.roomService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('" + RoleNames.ADMIN + "')")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		this.roomService.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/confirm-availability")
	@PreAuthorize("hasAnyRole('" + RoleNames.USER + "','" + RoleNames.ADMIN + "')")
	public ResponseEntity<Void> confirm(@PathVariable Long id, @Valid @RequestBody ConfirmAvailabilityRequest request) {
		this.roomAvailabilityService.confirm(id, request);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/{id}/release")
	@PreAuthorize("hasAnyRole('" + RoleNames.USER + "','" + RoleNames.ADMIN + "')")
	public ResponseEntity<Void> release(@PathVariable Long id, @Valid @RequestBody ReleaseRoomRequest request) {
		this.roomAvailabilityService.release(id, request);
		return ResponseEntity.ok().build();
	}

}
