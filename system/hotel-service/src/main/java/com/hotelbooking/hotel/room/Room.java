package com.hotelbooking.hotel.room;

import com.hotelbooking.hotel.hotel.Hotel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "rooms", uniqueConstraints = @UniqueConstraint(columnNames = { "hotel_id", "number" }),
		indexes = @Index(name = "idx_rooms_recommendation", columnList = "available, times_booked, id"))
@Getter
@Setter
@NoArgsConstructor
public class Room {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "hotel_id", nullable = false)
	private Hotel hotel;

	@Column(nullable = false, length = 255)
	private String number;

	@Column(nullable = false)
	private boolean available;

	@Column(name = "times_booked", nullable = false)
	private int timesBooked;

}
