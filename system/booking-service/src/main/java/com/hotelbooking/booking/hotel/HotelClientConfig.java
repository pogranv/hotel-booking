package com.hotelbooking.booking.hotel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HotelClientConfig {

	@Bean
	@LoadBalanced
	public RestClient.Builder hotelRestClientBuilder() {
		return RestClient.builder();
	}

	@Bean
	public RestClient hotelRestClient(@LoadBalanced RestClient.Builder hotelRestClientBuilder) {
		return hotelRestClientBuilder.baseUrl("lb://hotel-service").build();
	}

	@Bean(destroyMethod = "shutdown")
	public ExecutorService hotelClientExecutor() {
		ThreadFactory factory = runnable -> {
			Thread thread = new Thread(runnable);
			thread.setDaemon(true);
			return thread;
		};
		return Executors.newCachedThreadPool(factory);
	}

}
