package com.hotelbooking.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtConfigTest {

	private static final String SECRET = "0123456789abcdef0123456789abcdef";

	private static final String FOREIGN_SECRET = "fedcba9876543210fedcba9876543210";

	private AnnotationConfigApplicationContext context;

	private JwtDecoder decoder;

	private JwtAuthenticationConverter converter;

	private Instant issuedAt;

	private Instant expiresAt;

	@BeforeEach
	void setUp() {
		issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		expiresAt = issuedAt.plus(Duration.ofHours(1));
		context = new AnnotationConfigApplicationContext();
		context.getEnvironment()
			.getPropertySources()
			.addFirst(new MapPropertySource("jwt", Map.of("security.jwt.secret", SECRET)));
		context.register(JwtConfig.class);
		context.refresh();
		decoder = context.getBean("jwtDecoder", JwtDecoder.class);
		converter = context.getBean(JwtAuthenticationConverter.class);
	}

	@AfterEach
	void tearDown() {
		context.close();
	}

	@Test
	void acceptsUserTokenWithRoleUserAuthority() throws Exception {
		String token = signedToken(SECRET, "1", "USER", issuedAt, expiresAt);
		Jwt jwt = decoder.decode(token);
		assertNotNull(jwt);
		AbstractAuthenticationToken authentication = converter.convert(jwt);
		assertNotNull(authentication);
		assertIterableEquals(List.of("ROLE_USER"),
				authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
	}

	@Test
	void acceptsAdminTokenWithRoleAdminAuthority() throws Exception {
		String token = signedToken(SECRET, "1", "ADMIN", issuedAt, expiresAt);
		Jwt jwt = decoder.decode(token);
		assertNotNull(jwt);
		AbstractAuthenticationToken authentication = converter.convert(jwt);
		assertNotNull(authentication);
		assertIterableEquals(List.of("ROLE_ADMIN"),
				authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
	}

	@Test
	void rejectsExpiredTokenWithoutAuthentication() throws Exception {
		Instant expiredAt = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.SECONDS);
		Instant issued = expiredAt.minus(Duration.ofHours(1));
		String token = signedToken(SECRET, "1", "USER", issued, expiredAt);
		assertThrows(JwtException.class, () -> decoder.decode(token));
	}

	@Test
	void rejectsTokenSignedByForeignSecret() throws Exception {
		String token = signedToken(FOREIGN_SECRET, "1", "USER", issuedAt, expiresAt);
		assertThrows(JwtException.class, () -> decoder.decode(token));
	}

	@Test
	void rejectsTokenWithoutRoleClaim() throws Exception {
		String token = signedToken(SECRET, "1", null, issuedAt, expiresAt);
		assertThrows(JwtException.class, () -> decoder.decode(token));
	}

	private static String signedToken(String secret, String subject, String role, Instant issuedAt, Instant expiresAt)
			throws Exception {
		JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
		JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().subject(subject)
			.issueTime(Date.from(issuedAt))
			.expirationTime(Date.from(expiresAt));
		if (role != null) {
			claims.claim("role", role);
		}
		SignedJWT jwt = new SignedJWT(header, claims.build());
		jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
		return jwt.serialize();
	}

}
