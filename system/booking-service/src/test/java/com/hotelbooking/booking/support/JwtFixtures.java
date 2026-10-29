package com.hotelbooking.booking.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

public final class JwtFixtures {

	public static final String SECRET = "0123456789abcdef0123456789abcdef";

	private static final String FOREIGN_SECRET = "fedcba9876543210fedcba9876543210";

	private JwtFixtures() {
	}

	public static String validToken(long userId, String role) {
		Instant issuedAt = Instant.now();
		return sign(SECRET, userId, role, issuedAt, issuedAt.plusSeconds(3600));
	}

	public static String expiredToken(long userId, String role) {
		Instant expiresAt = Instant.now().minusSeconds(120);
		return sign(SECRET, userId, role, expiresAt.minusSeconds(3600), expiresAt);
	}

	public static String foreignSignedToken(long userId, String role) {
		Instant issuedAt = Instant.now();
		return sign(FOREIGN_SECRET, userId, role, issuedAt, issuedAt.plusSeconds(3600));
	}

	private static String sign(String secret, long userId, String role, Instant issuedAt, Instant expiresAt) {
		try {
			JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(Long.toString(userId))
				.claim("role", role)
				.issueTime(Date.from(issuedAt))
				.expirationTime(Date.from(expiresAt))
				.build();
			SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
			jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
			return jwt.serialize();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
