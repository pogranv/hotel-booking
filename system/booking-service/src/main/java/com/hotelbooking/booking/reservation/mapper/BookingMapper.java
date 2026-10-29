package com.hotelbooking.booking.reservation.mapper;

import com.hotelbooking.booking.reservation.dto.BookingResponse;
import com.hotelbooking.booking.reservation.persistence.BookingEntity;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookingMapper {

	@Mapping(target = "userId", source = "user.id")
	BookingResponse toResponse(BookingEntity entity);

}
