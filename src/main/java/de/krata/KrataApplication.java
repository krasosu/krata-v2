package de.krata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KrataApplication {

    public static void main(String[] args) {
        SpringApplication.run(KrataApplication.class, args);
    }
}
