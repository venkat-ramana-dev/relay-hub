package dev.venkat.relayhub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Left empty. Hides the background task engine from @WebMvcTests.
}
