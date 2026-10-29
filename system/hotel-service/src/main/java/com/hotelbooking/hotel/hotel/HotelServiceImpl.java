package com.hotelbooking.hotel.hotel;

import java.util.List;

import com.hotelbooking.hotel.error.HotelDeletionConflictException;
import com.hotelbooking.hotel.error.HotelNotFoundException;
import com.hotelbooking.hotel.hotel.dto.HotelCreateRequest;
import com.hotelbooking.hotel.hotel.dto.HotelResponse;
import com.hotelbooking.hotel.hotel.dto.HotelUpdateRequest;
import com.hotelbooking.hotel.room.RoomRepository;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class HotelServiceImpl implements HotelService {

	private final HotelRepository hotelRepository;

	private final RoomRepository roomRepository;

	private final HotelMapper hotelMapper;

	public HotelServiceImpl(HotelRepository hotelRepository, RoomRepository roomRepository, HotelMapper hotelMapper) {
		this.hotelRepository = hotelRepository;
		this.roomRepository = roomRepository;
		this.hotelMapper = hotelMapper;
	}

	@Override
	public HotelResponse create(HotelCreateRequest request) {
		Hotel hotel = new Hotel();
		hotel.setName(request.getName());
		hotel.setAddress(request.getAddress());
		return this.hotelMapper.toResponse(this.hotelRepository.save(hotel));
	}

	@Override
	public List<HotelResponse> findAll() {
		return this.hotelRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
			.stream()
			.map(this.hotelMapper::toResponse)
			.toList();
	}

	@Override
	public HotelResponse findById(Long id) {
		return this.hotelMapper.toResponse(this.hotelRepository.findById(id).orElseThrow(HotelNotFoundException::new));
	}

	@Override
	public HotelResponse update(Long id, HotelUpdateRequest request) {
		Hotel hotel = this.hotelRepository.findById(id).orElseThrow(HotelNotFoundException::new);
		hotel.setName(request.getName());
		hotel.setAddress(request.getAddress());
		return this.hotelMapper.toResponse(hotel);
	}

	@Override
	public void delete(Long id) {
		Hotel hotel = this.hotelRepository.findById(id).orElseThrow(HotelNotFoundException::new);
		if (this.roomRepository.existsByHotelId(id)) {
			throw new HotelDeletionConflictException();
		}
		this.hotelRepository.delete(hotel);
	}

}
