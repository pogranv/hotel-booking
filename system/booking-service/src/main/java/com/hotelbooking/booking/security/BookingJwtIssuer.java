package com.hotelbooking.booking.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import com.hotelbooking.booking.account.persistence.UserRole;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BookingJwtIssuer {

	private final String secret;

	public BookingJwtIssuer(@Value("${security.jwt.secret}") String secret) {
		this.secret = secret;
	}

	public String issue(long userId, UserRole role) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plusSeconds(3600);
		JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(Long.toString(userId))
			.claim("role", role.name())
			.issueTime(Date.from(issuedAt))
			.expirationTime(Date.from(expiresAt))
			.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
		try {
			jwt.sign(new MACSigner(this.secret.getBytes(StandardCharsets.UTF_8)));
		}
		catch (JOSEException ex) {
			throw new IllegalStateException("JWT signing failed", ex);
		}
		return jwt.serialize();
	}

}
