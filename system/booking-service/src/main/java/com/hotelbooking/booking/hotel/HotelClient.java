package com.hotelbooking.booking.hotel;

import java.util.List;

import com.hotelbooking.booking.hotel.dto.ConfirmAvailabilityCommand;
import com.hotelbooking.booking.hotel.dto.ReleaseRoomCommand;
import com.hotelbooking.booking.hotel.dto.RoomRecommendation;

public interface HotelClient {

	List<RoomRecommendation> recommend();

	HotelConfirmStatus confirm(long roomId, ConfirmAvailabilityCommand command);

	void release(long roomId, ReleaseRoomCommand command);

}
