package com.hotelbooking.booking.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.hotelbooking.booking.error.ApiStatusException;
import com.hotelbooking.common.error.ErrorDtoFactory;
import com.hotelbooking.common.security.JwtConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
@Import(JwtConfig.class)
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	private static final String INVALID_TOKEN = "Недействительный токен";

	private static final String FORBIDDEN = "Недостаточно прав";

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(10);
	}

	@Bean
	public Jackson2ObjectMapperBuilderCustomizer strictBooleanCoercion() {
		return builder -> builder.postConfigurer(mapper -> mapper.coercionConfigFor(LogicalType.Boolean)
			.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
			.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
			.setCoercion(CoercionInputShape.Float, CoercionAction.Fail));
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder,
			JwtAuthenticationConverter jwtAuthenticationConverter, ObjectMapper objectMapper, CurrentUser currentUser)
			throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.POST, "/user/register", "/user/auth")
				.permitAll()
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
				.permitAll()
				.anyRequest()
				.authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2.bearerTokenResolver(bearerTokenResolver())
				.jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter))
				.authenticationEntryPoint((request, response, exception) -> writeError(request, response, 401,
						INVALID_TOKEN, objectMapper))
				.accessDeniedHandler(
						(request, response, exception) -> writeError(request, response, 403, FORBIDDEN, objectMapper)))
			.addFilterAfter(deletedUserFilter(currentUser, objectMapper), BearerTokenAuthenticationFilter.class);
		return http.build();
	}

	private static OncePerRequestFilter deletedUserFilter(CurrentUser currentUser, ObjectMapper objectMapper) {
		return new OncePerRequestFilter() {
			@Override
			protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
					FilterChain filterChain) throws ServletException, IOException {
				Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
				if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
					try {
						currentUser.requireId(jwt);
					}
					catch (ApiStatusException ex) {
						SecurityContextHolder.clearContext();
						writeError(request, response, 401, INVALID_TOKEN, objectMapper);
						return;
					}
				}
				filterChain.doFilter(request, response);
			}
		};
	}

	private static BearerTokenResolver bearerTokenResolver() {
		DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
		return request -> {
			if (HttpMethod.POST.matches(request.getMethod()) && ("/user/register".equals(request.getRequestURI())
					|| "/user/auth".equals(request.getRequestURI()))) {
				return null;
			}
			return delegate.resolve(request);
		};
	}

	private static void writeError(HttpServletRequest request, HttpServletResponse response, int status, String message,
			ObjectMapper objectMapper) throws IOException {
		if (status == 401 || status == 403 || status == 404) {
			log.error(message);
		}
		response.setStatus(status);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(),
				ErrorDtoFactory.of(status, message, request.getRequestURI()));
	}

}
