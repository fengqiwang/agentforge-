package com.agentforge.seeder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication(scanBasePackages = "com.agentforge.seeder")
public class DataSeederApp {

    public static void main(String[] args) {
        log.info("Seeder ready");
        SpringApplication.run(DataSeederApp.class, args);
    }
}
