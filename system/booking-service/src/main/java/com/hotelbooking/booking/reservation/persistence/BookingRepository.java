package com.hotelbooking.booking.reservation.persistence;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {

	Optional<BookingEntity> findByRequestId(String requestId);

	boolean existsByUserId(Long userId);

	Page<BookingEntity> findByUserId(Long userId, Pageable pageable);

}
