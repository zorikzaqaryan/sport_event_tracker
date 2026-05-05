package com.example.sportsevents.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;

@Configuration
public class SchedulerConfig {

    @Bean(destroyMethod = "shutdown")
    public TaskScheduler liveEventTaskScheduler(AppProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.polling().schedulerPoolSize());
        scheduler.setThreadNamePrefix("live-event-poll-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
