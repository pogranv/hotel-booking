package com.hotelbooking.hotel.hotel;

import java.util.List;

import com.hotelbooking.hotel.hotel.dto.HotelCreateRequest;
import com.hotelbooking.hotel.hotel.dto.HotelResponse;
import com.hotelbooking.hotel.hotel.dto.HotelUpdateRequest;

public interface HotelService {

	HotelResponse create(HotelCreateRequest request);

	List<HotelResponse> findAll();

	HotelResponse findById(Long id);

	HotelResponse update(Long id, HotelUpdateRequest request);

	void delete(Long id);

}
