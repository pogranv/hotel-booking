package com.hotelbooking.hotel.room;

import java.util.List;

import com.hotelbooking.hotel.room.dto.RoomCreateRequest;
import com.hotelbooking.hotel.room.dto.RoomResponse;
import com.hotelbooking.hotel.room.dto.RoomUpdateRequest;

public interface RoomService {

	RoomResponse create(RoomCreateRequest request);

	List<RoomResponse> findAvailable(Long hotelId);

	RoomResponse findById(Long id);

	RoomResponse update(Long id, RoomUpdateRequest request);

	void delete(Long id);

}
