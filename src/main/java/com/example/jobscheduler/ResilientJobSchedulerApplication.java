package com.example.jobscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ResilientJobSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResilientJobSchedulerApplication.class, args);
    }

}
