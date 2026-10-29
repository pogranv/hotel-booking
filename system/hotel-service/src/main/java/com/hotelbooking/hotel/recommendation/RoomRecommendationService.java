package com.hotelbooking.hotel.recommendation;

import java.util.List;

import com.hotelbooking.hotel.room.dto.RoomResponse;

public interface RoomRecommendationService {

	List<RoomResponse> recommend();

}
