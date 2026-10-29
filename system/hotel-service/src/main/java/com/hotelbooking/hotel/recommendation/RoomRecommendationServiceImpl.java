package com.hotelbooking.hotel.recommendation;

import java.util.List;

import com.hotelbooking.hotel.room.RoomMapper;
import com.hotelbooking.hotel.room.RoomRepository;
import com.hotelbooking.hotel.room.dto.RoomResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class RoomRecommendationServiceImpl implements RoomRecommendationService {

	private final RoomRepository roomRepository;

	private final RoomMapper roomMapper;

	public RoomRecommendationServiceImpl(RoomRepository roomRepository, RoomMapper roomMapper) {
		this.roomRepository = roomRepository;
		this.roomMapper = roomMapper;
	}

	@Override
	public List<RoomResponse> recommend() {
		return this.roomRepository.findForRecommendation().stream().map(this.roomMapper::toResponse).toList();
	}

}
