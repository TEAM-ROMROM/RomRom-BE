package com.romrom.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableJpaAuditing
@EnableMongoAuditing
@EnableJpaRepositories(basePackages = {
    "com.romrom.member.repository",
    "com.romrom.item.repository",
})
@EnableMongoRepositories(basePackages = {
    "com.romrom.item.repository.mongo"
})
@ComponentScan(basePackages = {
    "com.romrom.common",
    "com.romrom.member",
    "com.romrom.auth",
    "com.romrom.item",
    "com.romrom.ai",
    "com.romrom.application",
    "com.romrom.web"
})
@EntityScan(basePackages = {
    "com.romrom.member.entity",
    "com.romrom.auth.entity",
    "com.romrom.item.entity",
    "com.romrom.ai.entity"
})
public class RomBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(RomBackApplication.class, args);
    }

}
