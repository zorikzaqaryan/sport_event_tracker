package com.example.sportsevents.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sportsEventsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Sports Live Events Service")
                .version("0.0.1")
                .description("Tracks live sports events. For each LIVE event, polls an external score API every 10 seconds and publishes score updates to Kafka."));
    }
}
