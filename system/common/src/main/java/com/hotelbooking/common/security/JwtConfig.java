package com.hotelbooking.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
public class JwtConfig {

	@Bean
	public JwtDecoder jwtDecoder(@Value("${security.jwt.secret}") String secret) {
		byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (keyBytes.length < 32) {
			throw new IllegalStateException("security.jwt.secret must be at least 32 bytes");
		}
		SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
				new JwtTimestampValidator(Duration.ZERO), new RoleAndSubjectValidator());
		decoder.setJwtValidator(validator);
		return decoder;
	}

	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
		authoritiesConverter.setAuthoritiesClaimName("role");
		authoritiesConverter.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
		return converter;
	}

	private static final class RoleAndSubjectValidator implements OAuth2TokenValidator<Jwt> {

		@Override
		public OAuth2TokenValidatorResult validate(Jwt token) {
			String subject = token.getSubject();
			Object role = token.getClaim("role");
			if (subject == null || !subject.matches("[0-9]+")) {
				return failure();
			}
			if (!(role instanceof String roleName)) {
				return failure();
			}
			if (!RoleNames.USER.equals(roleName) && !RoleNames.ADMIN.equals(roleName)) {
				return failure();
			}
			return OAuth2TokenValidatorResult.success();
		}

		private static OAuth2TokenValidatorResult failure() {
			return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
		}

	}

}
