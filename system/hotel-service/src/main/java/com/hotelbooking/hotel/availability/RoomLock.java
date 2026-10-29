package com.hotelbooking.hotel.availability;

import java.time.Instant;
import java.time.LocalDate;

import com.hotelbooking.hotel.room.Room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "room_locks",
		uniqueConstraints = { @UniqueConstraint(columnNames = "booking_id"),
				@UniqueConstraint(columnNames = "request_id") },
		indexes = @Index(name = "idx_room_locks_overlap", columnList = "room_id, status, start_date, end_date"))
@Getter
@Setter
@NoArgsConstructor
public class RoomLock {

	public enum Status {

		ACTIVE, RELEASED

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "room_id", nullable = false)
	private Room room;

	@Column(name = "booking_id", nullable = false)
	private Long bookingId;

	@Column(name = "request_id", nullable = false, length = 255)
	private String requestId;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 255)
	private Status status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

}
