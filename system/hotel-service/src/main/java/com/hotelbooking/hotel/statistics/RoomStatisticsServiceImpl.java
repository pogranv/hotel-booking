package com.hotelbooking.hotel.statistics;

import java.util.List;

import com.hotelbooking.hotel.room.RoomRepository;
import com.hotelbooking.hotel.statistics.dto.RoomStatisticsItem;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class RoomStatisticsServiceImpl implements RoomStatisticsService {

	private final RoomRepository roomRepository;

	private final RoomStatisticsMapper roomStatisticsMapper;

	public RoomStatisticsServiceImpl(RoomRepository roomRepository, RoomStatisticsMapper roomStatisticsMapper) {
		this.roomRepository = roomRepository;
		this.roomStatisticsMapper = roomStatisticsMapper;
	}

	@Override
	public List<RoomStatisticsItem> findStatistics(Long hotelId) {
		return this.roomRepository.findForStatistics(hotelId).stream().map(this.roomStatisticsMapper::toItem).toList();
	}

}
