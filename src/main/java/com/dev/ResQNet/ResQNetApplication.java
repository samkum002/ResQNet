package com.dev.ResQNet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableScheduling
@EnableTransactionManagement
@SpringBootApplication(exclude = SecurityAutoConfiguration.class)
public class ResQNetApplication {

	public static void main(String[] args) {
		SpringApplication.run(ResQNetApplication.class, args);
	}

	@Bean
	public MongoTransactionManager transactionManager(MongoDatabaseFactory factory){
		return new MongoTransactionManager(factory);
	}
}
