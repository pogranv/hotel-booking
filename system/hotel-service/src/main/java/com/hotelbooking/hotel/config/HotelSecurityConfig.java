package com.hotelbooking.hotel.config;

import com.hotelbooking.common.security.JwtConfig;
import com.hotelbooking.hotel.error.HotelAccessDeniedHandler;
import com.hotelbooking.hotel.error.HotelAuthenticationEntryPoint;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Import(JwtConfig.class)
public class HotelSecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
			Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
			HotelAuthenticationEntryPoint authenticationEntryPoint, HotelAccessDeniedHandler accessDeniedHandler)
			throws Exception {
		http.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**")
				.permitAll()
				.anyRequest()
				.authenticated())
			.exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler)
				.jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter)));
		return http.build();
	}

}
