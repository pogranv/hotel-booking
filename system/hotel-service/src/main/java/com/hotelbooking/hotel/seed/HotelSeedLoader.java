package com.hotelbooking.hotel.seed;

import java.time.Instant;
import java.time.LocalDate;

import com.hotelbooking.hotel.hotel.HotelRepository;

import jakarta.persistence.EntityManager;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HotelSeedLoader implements ApplicationRunner {

	private final HotelRepository hotelRepository;

	private final EntityManager entityManager;

	private final ObjectProvider<HotelSeedLoader> self;

	public HotelSeedLoader(HotelRepository hotelRepository, EntityManager entityManager,
			ObjectProvider<HotelSeedLoader> self) {
		this.hotelRepository = hotelRepository;
		this.entityManager = entityManager;
		this.self = self;
	}

	@Override
	public void run(ApplicationArguments args) {
		this.self.getObject().seed();
	}

	@Transactional
	public void seed() {
		if (this.hotelRepository.existsById(1L)) {
			return;
		}
		insertHotel(1L, "Hotel A", "Address A");
		insertHotel(2L, "Hotel B", "Address B");
		insertRoom(1L, 1L, "101", true, 0);
		insertRoom(2L, 1L, "102", true, 0);
		insertRoom(3L, 1L, "103", true, 1);
		insertRoom(4L, 1L, "104", false, 0);
		insertRoom(5L, 2L, "201", true, 1);
		insertLock(1L, 1L, 3L, "seed-booking-1", LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-12"), "ACTIVE",
				Instant.parse("2026-10-01T00:00:00Z"));
		insertLock(2L, 2L, 5L, "seed-booking-2", LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-03"), "ACTIVE",
				Instant.parse("2026-10-02T00:00:00Z"));
		this.entityManager.createNativeQuery("ALTER TABLE hotels ALTER COLUMN id RESTART WITH 3").executeUpdate();
		this.entityManager.createNativeQuery("ALTER TABLE rooms ALTER COLUMN id RESTART WITH 6").executeUpdate();
		this.entityManager.createNativeQuery("ALTER TABLE room_locks ALTER COLUMN id RESTART WITH 3").executeUpdate();
	}

	private void insertHotel(long id, String name, String address) {
		this.entityManager.createNativeQuery("INSERT INTO hotels (id, name, address) VALUES (:id, :name, :address)")
			.setParameter("id", id)
			.setParameter("name", name)
			.setParameter("address", address)
			.executeUpdate();
	}

	private void insertRoom(long id, long hotelId, String number, boolean available, int timesBooked) {
		this.entityManager.createNativeQuery(
				"INSERT INTO rooms (id, hotel_id, number, available, times_booked) VALUES (:id, :hotelId, :number, :available, :timesBooked)")
			.setParameter("id", id)
			.setParameter("hotelId", hotelId)
			.setParameter("number", number)
			.setParameter("available", available)
			.setParameter("timesBooked", timesBooked)
			.executeUpdate();
	}

	private void insertLock(long id, long bookingId, long roomId, String requestId, LocalDate startDate,
			LocalDate endDate, String status, Instant createdAt) {
		this.entityManager.createNativeQuery("""
				INSERT INTO room_locks (id, booking_id, room_id, request_id, start_date, end_date, status, created_at)
				VALUES (:id, :bookingId, :roomId, :requestId, :startDate, :endDate, :status, :createdAt)
				""")
			.setParameter("id", id)
			.setParameter("bookingId", bookingId)
			.setParameter("roomId", roomId)
			.setParameter("requestId", requestId)
			.setParameter("startDate", startDate)
			.setParameter("endDate", endDate)
			.setParameter("status", status)
			.setParameter("createdAt", createdAt)
			.executeUpdate();
	}

}
