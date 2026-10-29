package com.hotelbooking.hotel.availability;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomLockRepository extends JpaRepository<RoomLock, Long> {

	Optional<RoomLock> findByRequestId(String requestId);

	Optional<RoomLock> findByBookingId(Long bookingId);

	@Query("""
			select l from RoomLock l
			where l.room.id = :roomId and l.bookingId = :bookingId and l.status = :status
			""")
	Optional<RoomLock> findByRoomIdAndBookingIdAndStatus(@Param("roomId") Long roomId,
			@Param("bookingId") Long bookingId, @Param("status") RoomLock.Status status);

	@Query("""
			select l from RoomLock l
			where l.room.id = :roomId
			and l.status = com.hotelbooking.hotel.availability.RoomLock.Status.ACTIVE
			and l.startDate < :requestedEnd
			and :requestedStart < l.endDate
			""")
	Optional<RoomLock> findActiveOverlap(@Param("roomId") Long roomId,
			@Param("requestedStart") LocalDate requestedStart, @Param("requestedEnd") LocalDate requestedEnd);

	@Query("select l from RoomLock l where l.room.id = :roomId")
	List<RoomLock> findByRoomId(@Param("roomId") Long roomId);

}
