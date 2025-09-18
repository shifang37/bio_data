package com.example.bio_data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BioDataApplication {

	public static void main(String[] args) {
		SpringApplication.run(BioDataApplication.class, args);
	}

}
