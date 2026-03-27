package com.cottageai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class CottageServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CottageServiceApplication.class, args);
    }
}
