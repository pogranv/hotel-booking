package com.hotelbooking.hotel.hotel;

import com.hotelbooking.hotel.hotel.dto.HotelResponse;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface HotelMapper {

	HotelResponse toResponse(Hotel hotel);

}
