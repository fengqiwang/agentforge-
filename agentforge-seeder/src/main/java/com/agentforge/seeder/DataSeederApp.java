package com.agentforge.seeder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.agentforge.seeder")
public class DataSeederApp {

    public static void main(String[] args) {
        System.out.println("Seeder ready");
        SpringApplication.run(DataSeederApp.class, args);
    }
}
