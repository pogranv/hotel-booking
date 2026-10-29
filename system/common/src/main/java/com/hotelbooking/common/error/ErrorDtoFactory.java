package com.hotelbooking.common.error;

import java.time.Instant;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import com.hotelbooking.common.trace.TraceConstants;

public final class ErrorDtoFactory {

	private ErrorDtoFactory() {
	}

	public static ErrorDto of(int status, String message, String path) {
		String traceId = MDC.get(TraceConstants.MDC_TRACE_ID);
		String error = HttpStatus.valueOf(status).getReasonPhrase();
		return new ErrorDto(Instant.now(), status, error, message, path, traceId);
	}

}
