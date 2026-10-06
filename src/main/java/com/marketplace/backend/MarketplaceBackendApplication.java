package com.marketplace.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MarketplaceBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(
				MarketplaceBackendApplication.class,
				args
		);
	}
}