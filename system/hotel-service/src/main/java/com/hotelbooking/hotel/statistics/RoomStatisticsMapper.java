package com.hotelbooking.hotel.statistics;

import com.hotelbooking.hotel.room.Room;
import com.hotelbooking.hotel.statistics.dto.RoomStatisticsItem;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoomStatisticsMapper {

	@Mapping(target = "hotelId", source = "hotel.id")
	RoomStatisticsItem toItem(Room room);

}
