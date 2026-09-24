package com.vipul.agrishare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync // needed later for FCM push + notification jobs
public class AgrishareApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgrishareApplication.class, args);
    }
}
