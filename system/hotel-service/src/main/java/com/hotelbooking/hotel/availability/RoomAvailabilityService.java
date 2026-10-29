package com.hotelbooking.hotel.availability;

import com.hotelbooking.hotel.availability.dto.ConfirmAvailabilityRequest;
import com.hotelbooking.hotel.availability.dto.ReleaseRoomRequest;

public interface RoomAvailabilityService {

	void confirm(Long roomId, ConfirmAvailabilityRequest request);

	void release(Long roomId, ReleaseRoomRequest request);

}
