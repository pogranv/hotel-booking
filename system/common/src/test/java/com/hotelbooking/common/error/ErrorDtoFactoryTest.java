package com.hotelbooking.common.error;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ErrorDtoFactoryTest {

	@Test
	void ofCopiesMdcTraceIdAndArgumentFields() {
		MDC.put("traceId", "значение-из-mdc");
		try {
			ErrorDto dto = ErrorDtoFactory.of(401, "текст передает вызывающий", "/api/bookings");
			assertEquals("значение-из-mdc", dto.traceId());
			assertEquals(401, dto.status());
			assertEquals("текст передает вызывающий", dto.message());
			assertEquals("/api/bookings", dto.path());
			assertEquals("Unauthorized", dto.error());
			assertInstanceOf(Instant.class, dto.timestamp());
		}
		finally {
			MDC.clear();
		}
	}

}
