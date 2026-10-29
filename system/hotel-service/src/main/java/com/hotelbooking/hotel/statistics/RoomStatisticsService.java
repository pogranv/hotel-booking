package com.hotelbooking.hotel.statistics;

import java.util.List;

import com.hotelbooking.hotel.statistics.dto.RoomStatisticsItem;

public interface RoomStatisticsService {

	List<RoomStatisticsItem> findStatistics(Long hotelId);

}
