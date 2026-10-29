package com.hotelbooking.hotel.error;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelbooking.common.error.ErrorDto;
import com.hotelbooking.common.error.ErrorDtoFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class HotelAccessDeniedHandler implements AccessDeniedHandler {

	private static final Logger log = LoggerFactory.getLogger(HotelAccessDeniedHandler.class);

	private final ObjectMapper objectMapper;

	public HotelAccessDeniedHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		log.error("Недостаточно прав", accessDeniedException);
		ErrorDto body = ErrorDtoFactory.of(HttpServletResponse.SC_FORBIDDEN, "Недостаточно прав",
				request.getRequestURI());
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		this.objectMapper.writeValue(response.getOutputStream(), body);
	}

}
