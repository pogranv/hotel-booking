package com.hotelbooking.gateway;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.hotelbooking.common.error.ErrorDto;
import com.hotelbooking.common.trace.TraceConstants;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "eureka.client.enabled=false")
@AutoConfigureWebTestClient
class GatewayRoutingTest {

	private static final WireMockServer WIRE_MOCK = startWireMock();

	private static final String TRACE_ID_PATTERN = "[0-9a-f]{32}";

	private static WireMockServer startWireMock() {
		int port = freePort();
		WireMockServer server = new WireMockServer(wireMockConfig().port(port));
		server.start();
		return server;
	}

	private static int freePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			socket.setReuseAddress(true);
			return socket.getLocalPort();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Autowired
	private WebTestClient webTestClient;

	@Autowired
	private RouteDefinitionLocator routeDefinitionLocator;

	@DynamicPropertySource
	static void discoveryInstances(DynamicPropertyRegistry registry) {
		String uri = "http://127.0.0.1:" + WIRE_MOCK.port();
		registry.add("spring.cloud.discovery.client.simple.instances.booking-service[0].uri", () -> uri);
		registry.add("spring.cloud.discovery.client.simple.instances.hotel-service[0].uri", () -> uri);
	}

	@BeforeEach
	void resetDownstream() {
		WIRE_MOCK.resetAll();
	}

	@AfterAll
	static void stopDownstream() {
		WIRE_MOCK.stop();
	}

	@Test
	void publicPostsWithoutTokenStripApiPrefix() {
		WIRE_MOCK.stubFor(post(urlEqualTo("/user/register"))
			.willReturn(aResponse().withStatus(201).withBody("{\"result\":\"register\"}")));
		WIRE_MOCK.stubFor(post(urlEqualTo("/user/auth"))
			.willReturn(aResponse().withStatus(200).withBody("{\"result\":\"auth\"}")));

		this.webTestClient.post()
			.uri("/api/user/register")
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody()
			.json("{\"result\":\"register\"}");
		this.webTestClient.post()
			.uri("/api/user/auth")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.json("{\"result\":\"auth\"}");

		WIRE_MOCK.verify(1, postRequestedFor(urlEqualTo("/user/register")).withoutHeader(HttpHeaders.AUTHORIZATION));
		WIRE_MOCK.verify(1, postRequestedFor(urlEqualTo("/user/auth")).withoutHeader(HttpHeaders.AUTHORIZATION));
		WIRE_MOCK.verify(0, postRequestedFor(urlEqualTo("/api/user/register")));
		WIRE_MOCK.verify(0, postRequestedFor(urlEqualTo("/api/user/auth")));
	}

	@Test
	void closedRouteWithoutAuthorizationReturns401() {
		this.webTestClient.get()
			.uri("/api/bookings")
			.exchange()
			.expectStatus()
			.isUnauthorized()
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody(ErrorDto.class)
			.value(dto -> assertError(dto, HttpStatus.UNAUTHORIZED.value(), "Unauthorized",
					"Требуется заголовок Authorization", "/api/bookings"));

		assertThat(WIRE_MOCK.getAllServeEvents()).isEmpty();
	}

	@Test
	void forwardsAuthorizationWithoutSignatureCheck() {
		WIRE_MOCK.stubFor(get(urlEqualTo("/bookings"))
			.willReturn(aResponse().withStatus(200).withBody("{\"result\":\"bookings\"}")));

		this.webTestClient.get()
			.uri("/api/bookings")
			.header(HttpHeaders.AUTHORIZATION, "not-a-jwt")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.json("{\"result\":\"bookings\"}");

		WIRE_MOCK.verify(
				getRequestedFor(urlEqualTo("/bookings")).withHeader(HttpHeaders.AUTHORIZATION, equalTo("not-a-jwt")));
		WIRE_MOCK.verify(0, getRequestedFor(urlEqualTo("/api/bookings")));
	}

	@Test
	void confirmAvailabilityAndReleaseReturn404WithoutDownstreamCall() {
		assertInternalNotFound("/api/rooms/1/confirm-availability", "not-a-jwt");
		assertInternalNotFound("/api/rooms/1/release", "not-a-jwt");
		assertThat(WIRE_MOCK.getAllServeEvents()).isEmpty();
	}

	@Test
	void missingTraceIdIsUuidWithoutHyphensAndEntersErrorDto() {
		WIRE_MOCK.stubFor(post(urlEqualTo("/user/register"))
			.willReturn(aResponse().withStatus(201).withBody("{\"result\":\"register\"}")));

		this.webTestClient.post().uri("/api/user/register").exchange().expectStatus().isCreated();

		LoggedRequest publicRequest = WIRE_MOCK.getAllServeEvents().get(0).getRequest();
		String publicTraceId = publicRequest.getHeader(TraceConstants.HEADER);
		assertThat(publicTraceId).matches(TRACE_ID_PATTERN);
		assertThat(publicTraceId).doesNotContain("-");

		this.webTestClient.get()
			.uri("/api/bookings")
			.exchange()
			.expectStatus()
			.isUnauthorized()
			.expectBody(ErrorDto.class)
			.value(dto -> {
				assertThat(dto.traceId()).matches(TRACE_ID_PATTERN);
				assertThat(dto.traceId()).doesNotContain("-");
				assertThat(dto.traceId()).isNotEqualTo(publicTraceId);
			});
	}

	@Test
	void traceHeaderIsAbsentFromClientResponse() {
		WIRE_MOCK.stubFor(post(urlEqualTo("/user/register"))
			.willReturn(aResponse().withStatus(201).withBody("{\"result\":\"register\"}")));

		this.webTestClient.get()
			.uri("/api/bookings")
			.exchange()
			.expectStatus()
			.isUnauthorized()
			.expectHeader()
			.doesNotExist(TraceConstants.HEADER)
			.expectBody(ErrorDto.class)
			.value(dto -> assertThat(dto.traceId()).isNotBlank());

		this.webTestClient.post()
			.uri("/api/user/register")
			.exchange()
			.expectStatus()
			.isCreated()
			.expectHeader()
			.doesNotExist(TraceConstants.HEADER);
	}

	@Test
	void routeUrisAreLoadBalancedWithoutFixedPorts() {
		assertLoadBalancedRoutes();
	}

	@Test
	void internalSuffixWithoutAuthorizationReturns404() {
		assertInternalNotFound("/api/rooms/1/confirm-availability", null);
		assertInternalNotFound("/api/rooms/1/release", null);
		assertThat(WIRE_MOCK.getAllServeEvents()).isEmpty();
	}

	@Test
	void pathOutsideFiveRoutesReturns404ErrorDto() {
		assertRouteNotFound("/api/other");
		this.webTestClient.get()
			.uri("/unknown")
			.header(HttpHeaders.AUTHORIZATION, "not-a-jwt")
			.exchange()
			.expectStatus()
			.isNotFound()
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody(ErrorDto.class)
			.value(dto -> assertError(dto, HttpStatus.NOT_FOUND.value(), "Not Found", "Маршрут не найден", "/unknown"));
		assertThat(WIRE_MOCK.getAllServeEvents()).isEmpty();
		assertLoadBalancedRoutes();
		assertThat(definitions()).extracting(RouteDefinition::getId).doesNotContain("/**");
		assertThat(definitions()).allSatisfy(route -> assertThat(pathArguments(route)).noneMatch("/**"::equals));
	}

	@Test
	void downstream503IsPassedThrough() {
		WIRE_MOCK.stubFor(
				get(urlEqualTo("/bookings")).willReturn(aResponse().withStatus(503).withBody("{\"result\":\"down\"}")));

		this.webTestClient.get()
			.uri("/api/bookings")
			.header(HttpHeaders.AUTHORIZATION, "not-a-jwt")
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
			.expectBody()
			.json("{\"result\":\"down\"}")
			.jsonPath("$.timestamp")
			.doesNotExist()
			.jsonPath("$.traceId")
			.doesNotExist();
	}

	@Test
	void connectionBreakReturns503ErrorDto() {
		try {
			WIRE_MOCK.stop();
			this.webTestClient.get()
				.uri("/api/bookings")
				.header(HttpHeaders.AUTHORIZATION, "not-a-jwt")
				.exchange()
				.expectStatus()
				.isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
				.expectHeader()
				.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
				.expectHeader()
				.doesNotExist(TraceConstants.HEADER)
				.expectBody(ErrorDto.class)
				.value(dto -> {
					assertThat(dto.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
					assertThat(dto.error()).isEqualTo("Service Unavailable");
					assertThat(dto.message()).isEqualTo("Сервис недоступен");
					assertThat(dto.path()).isEqualTo("/api/bookings");
					assertThat(dto.traceId()).matches(TRACE_ID_PATTERN);
					assertThat(dto.traceId()).doesNotContain("-");
					assertThat(dto.timestamp()).isNotNull();
				});
		}
		finally {
			if (!WIRE_MOCK.isRunning()) {
				WIRE_MOCK.start();
			}
		}
	}

	private void assertInternalNotFound(String path, String authorization) {
		WebTestClient.RequestHeadersSpec<?> request = this.webTestClient.post().uri(path);
		if (authorization != null) {
			request = request.header(HttpHeaders.AUTHORIZATION, authorization);
		}
		request.exchange()
			.expectStatus()
			.isNotFound()
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody(ErrorDto.class)
			.value(dto -> assertError(dto, HttpStatus.NOT_FOUND.value(), "Not Found", "Маршрут не найден", path));
	}

	private void assertRouteNotFound(String path) {
		this.webTestClient.get()
			.uri(path)
			.exchange()
			.expectStatus()
			.isNotFound()
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody(ErrorDto.class)
			.value(dto -> assertError(dto, HttpStatus.NOT_FOUND.value(), "Not Found", "Маршрут не найден", path));
	}

	private void assertError(ErrorDto dto, int status, String error, String message, String path) {
		assertThat(dto.status()).isEqualTo(status);
		assertThat(dto.error()).isEqualTo(error);
		assertThat(dto.message()).isEqualTo(message);
		assertThat(dto.path()).isEqualTo(path);
		assertThat(dto.traceId()).isNotBlank();
		assertThat(dto.timestamp()).isNotNull();
	}

	private void assertLoadBalancedRoutes() {
		List<RouteDefinition> routes = definitions();
		assertThat(routes).extracting(RouteDefinition::getId)
			.containsExactlyInAnyOrder("user", "booking", "bookings", "hotels", "rooms");
		assertThat(routes).allSatisfy(route -> {
			String uri = route.getUri().toString();
			assertThat(route.getUri().getScheme()).isEqualTo("lb");
			assertThat(uri).doesNotContain("http")
				.doesNotContain("https")
				.doesNotContain("8081")
				.doesNotContain("8082");
		});
		assertRoute(routes, "user", "lb://booking-service", "/api/user", true);
		assertRoute(routes, "booking", "lb://booking-service", "/api/booking", true);
		assertRoute(routes, "bookings", "lb://booking-service", "/api/bookings", true);
		assertRoute(routes, "hotels", "lb://hotel-service", "/api/hotels", false);
		assertRoute(routes, "rooms", "lb://hotel-service", "/api/rooms", false);
	}

	private void assertRoute(List<RouteDefinition> routes, String id, String uri, String path, boolean stripPrefix) {
		RouteDefinition route = routes.stream().filter(item -> id.equals(item.getId())).findFirst().orElseThrow();
		assertThat(route.getUri()).hasToString(uri);
		assertThat(pathArguments(route)).contains(path, path + "/**");
		if (stripPrefix) {
			assertThat(route.getFilters()).anySatisfy(filter -> {
				assertThat(filter.getName()).isEqualTo("StripPrefix");
				assertThat(filter.getArgs().values()).contains("1");
			});
		}
		else {
			assertThat(route.getFilters()).extracting(FilterDefinition::getName).doesNotContain("StripPrefix");
		}
	}

	private List<String> pathArguments(RouteDefinition route) {
		return route.getPredicates()
			.stream()
			.filter(predicate -> "Path".equals(predicate.getName()))
			.map(PredicateDefinition::getArgs)
			.flatMap(args -> args.values().stream())
			.toList();
	}

	private List<RouteDefinition> definitions() {
		List<RouteDefinition> routes = this.routeDefinitionLocator.getRouteDefinitions().collectList().block();
		assertThat(routes).isNotNull();
		return routes;
	}

}
