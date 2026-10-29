package com.hotelbooking.eureka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.WebServer;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
class EurekaServerApplicationTest {

	@Autowired
	private Environment environment;

	@Autowired(required = false)
	private WebServer webServer;

	@Test
	void contextLoads() {
		assertThat(environment).isNotNull();
	}

	@Test
	void doesNotBindPort8761() {
		assertThat(webServer).isNull();
	}

	@Test
	void registerWithEurekaIsFalse() {
		assertThat(environment.getProperty("eureka.client.register-with-eureka", Boolean.class)).isFalse();
	}

	@Test
	void fetchRegistryIsFalse() {
		assertThat(environment.getProperty("eureka.client.fetch-registry", Boolean.class)).isFalse();
	}

	@Test
	void serverPortIs8761() {
		assertThat(environment.getProperty("server.port", Integer.class)).isEqualTo(8761);
	}

	@Test
	void applicationClassHasEnableEurekaServer() {
		assertThat(EurekaServerApplication.class.isAnnotationPresent(EnableEurekaServer.class)).isTrue();
	}

}
