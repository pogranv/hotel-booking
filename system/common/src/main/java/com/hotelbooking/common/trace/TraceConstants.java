package com.hotelbooking.common.trace;

public final class TraceConstants {

	public static final String HEADER = "X-Trace-Id";

	public static final String MDC_TRACE_ID = "traceId";

	public static final String MDC_BOOKING_ID = "bookingId";

	public static final String MDC_REQUEST_ID = "requestId";

	public static final String MDC_EVENT = "event";

	private TraceConstants() {
	}

}
