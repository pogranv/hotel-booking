package com.hotelbooking.hotel.room;

import java.util.List;

import com.hotelbooking.hotel.availability.RoomLock;
import com.hotelbooking.hotel.availability.RoomLockRepository;
import com.hotelbooking.hotel.error.DuplicateRoomNumberException;
import com.hotelbooking.hotel.error.HotelNotFoundException;
import com.hotelbooking.hotel.error.RoomDeletionConflictException;
import com.hotelbooking.hotel.error.RoomNotFoundException;
import com.hotelbooking.hotel.hotel.Hotel;
import com.hotelbooking.hotel.hotel.HotelRepository;
import com.hotelbooking.hotel.room.dto.RoomCreateRequest;
import com.hotelbooking.hotel.room.dto.RoomResponse;
import com.hotelbooking.hotel.room.dto.RoomUpdateRequest;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class RoomServiceImpl implements RoomService {

	private final RoomRepository roomRepository;

	private final RoomLockRepository roomLockRepository;

	private final HotelRepository hotelRepository;

	private final RoomMapper roomMapper;

	public RoomServiceImpl(RoomRepository roomRepository, RoomLockRepository roomLockRepository,
			HotelRepository hotelRepository, RoomMapper roomMapper) {
		this.roomRepository = roomRepository;
		this.roomLockRepository = roomLockRepository;
		this.hotelRepository = hotelRepository;
		this.roomMapper = roomMapper;
	}

	@Override
	public RoomResponse create(RoomCreateRequest request) {
		Hotel hotel = this.hotelRepository.findById(request.getHotelId()).orElseThrow(HotelNotFoundException::new);
		if (this.roomRepository.existsByHotelIdAndNumber(hotel.getId(), request.getNumber())) {
			throw new DuplicateRoomNumberException();
		}
		Room room = new Room();
		room.setHotel(hotel);
		room.setNumber(request.getNumber());
		room.setAvailable(request.getAvailable() == null || request.getAvailable());
		room.setTimesBooked(0);
		try {
			return this.roomMapper.toResponse(this.roomRepository.saveAndFlush(room));
		}
		catch (DataIntegrityViolationException ex) {
			throw new DuplicateRoomNumberException();
		}
	}

	@Override
	public List<RoomResponse> findAvailable(Long hotelId) {
		return this.roomRepository.findAvailable(hotelId).stream().map(this.roomMapper::toResponse).toList();
	}

	@Override
	public RoomResponse findById(Long id) {
		return this.roomMapper.toResponse(this.roomRepository.findById(id).orElseThrow(RoomNotFoundException::new));
	}

	@Override
	public RoomResponse update(Long id, RoomUpdateRequest request) {
		Room room = this.roomRepository.findById(id).orElseThrow(RoomNotFoundException::new);
		if (!room.getNumber().equals(request.getNumber())
				&& this.roomRepository.existsByHotelIdAndNumber(room.getHotel().getId(), request.getNumber())) {
			throw new DuplicateRoomNumberException();
		}
		room.setNumber(request.getNumber());
		room.setAvailable(request.getAvailable());
		return this.roomMapper.toResponse(room);
	}

	@Override
	public void delete(Long id) {
		Room room = this.roomRepository.findById(id).orElseThrow(RoomNotFoundException::new);
		List<RoomLock> locks = this.roomLockRepository.findByRoomId(id);
		boolean active = locks.stream().anyMatch(lock -> lock.getStatus() == RoomLock.Status.ACTIVE);
		if (active) {
			throw new RoomDeletionConflictException();
		}
		this.roomLockRepository.deleteAll(locks);
		this.roomLockRepository.flush();
		this.roomRepository.delete(room);
	}

}
