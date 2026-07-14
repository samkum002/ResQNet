package com.dev.ResQNet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;

@SpringBootApplication(exclude = SecurityAutoConfiguration.class)
public class ResQNetApplication {

	public static void main(String[] args) {
		SpringApplication.run(ResQNetApplication.class, args);
	}

}
