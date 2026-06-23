package com.agentforge.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableAsync
@SpringBootApplication
@ComponentScan("com.agentforge")
public class AgentForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentForgeApplication.class, args);
        System.out.println("AgentForgeApplication 启动成功");
    }
}
