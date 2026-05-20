package com.yourcompany.codereview.config;

import com.yourcompany.codereview.service.CodeReviewOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Application configuration.
 * Registers RestTemplate and triggers the review pipeline on startup.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AppConfig implements ApplicationRunner {

    private final CodeReviewOrchestrator orchestrator;

    /**
     * RestTemplate with sensible timeouts for GitHub and Anthropic API calls.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(90))
                .build();
    }

    /**
     * Triggered automatically when the Spring Boot app starts.
     * Runs the review pipeline and exits cleanly.
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("=== Claude AI Code Review Starting ===");
        try {
            orchestrator.runReview();
            log.info("=== Review completed successfully ===");
            System.exit(0);
        } catch (Exception e) {
            log.error("=== Review failed: {} ===", e.getMessage(), e);
            System.exit(1);
        }
    }
}
