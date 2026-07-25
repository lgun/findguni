package com.findguni;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FindGuniApplication {

    public static void main(String[] args) {
        SpringApplication.run(FindGuniApplication.class, args);
    }
}
