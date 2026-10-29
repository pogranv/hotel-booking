package com.hotelbooking.hotel.room;

import com.hotelbooking.hotel.room.dto.RoomResponse;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoomMapper {

	@Mapping(target = "hotelId", source = "hotel.id")
	RoomResponse toResponse(Room room);

}
