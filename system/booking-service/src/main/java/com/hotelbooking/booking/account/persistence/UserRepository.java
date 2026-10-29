package com.hotelbooking.booking.account.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

	Optional<UserEntity> findByUsername(String username);

	boolean existsByUsername(String username);

	List<UserEntity> findAllByOrderByIdAsc();

}
