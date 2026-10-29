package com.hotelbooking.booking.reservation.persistence;

import java.time.Instant;
import java.time.LocalDate;

import com.hotelbooking.booking.account.persistence.UserEntity;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "bookings",
		uniqueConstraints = @UniqueConstraint(name = "uk_bookings_request_id", columnNames = "request_id"),
		indexes = @Index(name = "idx_bookings_user_created_id", columnList = "user_id, created_at, id"))
public class BookingEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	@OnDelete(action = OnDeleteAction.RESTRICT)
	private UserEntity user;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 255)
	private BookingStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "request_id", nullable = false, length = 255)
	private String requestId;

}
