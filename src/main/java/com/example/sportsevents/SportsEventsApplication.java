package com.example.sportsevents;

import com.example.sportsevents.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class SportsEventsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SportsEventsApplication.class, args);
    }
}
