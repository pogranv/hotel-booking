package com.hotelbooking.booking.seed;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class DemoDataSeeder implements ApplicationRunner {

	private final JdbcTemplate jdbcTemplate;

	private final PasswordEncoder passwordEncoder;

	private final PlatformTransactionManager transactionManager;

	public DemoDataSeeder(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
			PlatformTransactionManager transactionManager) {
		this.jdbcTemplate = jdbcTemplate;
		this.passwordEncoder = passwordEncoder;
		this.transactionManager = transactionManager;
	}

	@Override
	public void run(ApplicationArguments args) {
		new TransactionTemplate(this.transactionManager).executeWithoutResult(status -> seed());
	}

	private void seed() {
		List<Long> existing = this.jdbcTemplate.query("SELECT id FROM users WHERE id = ?",
				(rs, rowNum) -> rs.getLong(1), 1L);
		if (!existing.isEmpty()) {
			return;
		}
		insertUser(1L, "admin", "admin", "ADMIN");
		insertUser(2L, "user1", "user1", "USER");
		insertUser(3L, "user2", "user2", "USER");
		insertBooking(1L, 2L, 3L, "2026-11-10", "2026-11-12", "CONFIRMED", "seed-booking-1", "2026-10-01T00:00:00Z");
		insertBooking(2L, 3L, 5L, "2026-12-01", "2026-12-03", "CONFIRMED", "seed-booking-2", "2026-10-02T00:00:00Z");
		insertBooking(3L, 2L, 2L, "2026-11-01", "2026-11-02", "CANCELLED", "seed-booking-3", "2026-10-03T00:00:00Z");
		this.jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN id RESTART WITH 4");
		this.jdbcTemplate.execute("ALTER TABLE bookings ALTER COLUMN id RESTART WITH 4");
	}

	private void insertUser(long id, String username, String rawPassword, String role) {
		this.jdbcTemplate.update("INSERT INTO users (id, username, password, role) VALUES (?, ?, ?, ?)", id, username,
				this.passwordEncoder.encode(rawPassword), role);
	}

	private void insertBooking(long id, long userId, long roomId, String startDate, String endDate, String status,
			String requestId, String createdAt) {
		this.jdbcTemplate.update(
				"INSERT INTO bookings (id, user_id, room_id, start_date, end_date, status, request_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
				id, userId, roomId, Date.valueOf(LocalDate.parse(startDate)), Date.valueOf(LocalDate.parse(endDate)),
				status, requestId, Timestamp.from(Instant.parse(createdAt)));
	}

}
