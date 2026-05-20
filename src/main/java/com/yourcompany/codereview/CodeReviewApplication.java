package com.yourcompany.codereview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Claude AI Code Review - Spring Boot Application
 *
 * This application is invoked by GitHub Actions to:
 *   1. Fetch the PR diff from GitHub API
 *   2. Send the diff to Claude AI for review
 *   3. Post the review as a comment on the Pull Request
 *
 * Run via: java -jar claude-code-review.jar
 * All configuration is passed through environment variables (see application.yml).
 */
@SpringBootApplication
public class CodeReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeReviewApplication.class, args);
    }
}
