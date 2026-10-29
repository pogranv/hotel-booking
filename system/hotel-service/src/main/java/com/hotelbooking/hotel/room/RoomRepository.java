package com.hotelbooking.hotel.room;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Room r where r.id = :id")
	Optional<Room> findWithWriteLock(@Param("id") Long id);

	@Query("select count(r) > 0 from Room r where r.hotel.id = :hotelId")
	boolean existsByHotelId(@Param("hotelId") Long hotelId);

	@Query("select count(r) > 0 from Room r where r.hotel.id = :hotelId and r.number = :number")
	boolean existsByHotelIdAndNumber(@Param("hotelId") Long hotelId, @Param("number") String number);

	@Query("select r from Room r where r.available = true and (:hotelId is null or r.hotel.id = :hotelId)")
	List<Room> findAvailable(@Param("hotelId") Long hotelId);

	@Query("select r from Room r where r.available = true order by r.timesBooked asc, r.id asc")
	List<Room> findForRecommendation();

	@Query("""
			select r from Room r
			where (:hotelId is null or r.hotel.id = :hotelId)
			order by r.timesBooked asc, r.id asc
			""")
	List<Room> findForStatistics(@Param("hotelId") Long hotelId);

}
