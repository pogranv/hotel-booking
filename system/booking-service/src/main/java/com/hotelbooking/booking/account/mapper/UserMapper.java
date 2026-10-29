package com.hotelbooking.booking.account.mapper;

import com.hotelbooking.booking.account.dto.UserResponse;
import com.hotelbooking.booking.account.persistence.UserEntity;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

	UserResponse toResponse(UserEntity entity);

}
