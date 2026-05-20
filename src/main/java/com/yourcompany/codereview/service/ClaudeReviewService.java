package com.yourcompany.codereview.service;

import com.yourcompany.codereview.config.ReviewConfig;
import com.yourcompany.codereview.model.ClaudeMessage;
import com.yourcompany.codereview.model.ClaudeRequest;
import com.yourcompany.codereview.model.ClaudeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Sends the filtered diff to the Anthropic Claude API and returns
 * a structured Java/Spring Boot-focused code review.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeReviewService {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION  = "2023-06-01";

    /**
     * Java and Spring Boot specific review prompt.
     * Claude will focus on Java idioms, Spring patterns, and common pitfalls.
     */
    private static final String SYSTEM_PROMPT = """
            You are a senior Java and Spring Boot engineer performing a thorough code review.
            Analyse the provided diff and give clear, actionable feedback focused on:

            1. BUGS & CORRECTNESS
               - NullPointerExceptions (missing null checks, uninitialized fields)
               - Incorrect equals() / hashCode() implementations
               - Off-by-one errors, infinite loops, wrong conditional logic
               - Thread-safety issues (shared mutable state, missing synchronization)
               - Incorrect use of Optional (calling .get() without isPresent())

            2. SECURITY
               - SQL injection (string concatenation in queries vs. parameterized queries)
               - Missing input validation (@Valid, @NotNull, custom validators)
               - Hardcoded credentials or secrets in source code
               - Insecure deserialization
               - Exposed sensitive data in logs or API responses
               - Missing Spring Security configurations (@PreAuthorize, CSRF)

            3. SPRING BOOT SPECIFIC
               - Missing @Transactional on write operations
               - Incorrect bean scopes (@Singleton holding request state)
               - Circular dependency risks
               - Missing exception handling (@ControllerAdvice / @ExceptionHandler)
               - Inefficient JPA (N+1 queries, missing FetchType, no pagination)
               - Improper use of @Autowired vs constructor injection
               - application.properties secrets that should be in environment variables

            4. PERFORMANCE
               - Unnecessary object creation in loops
               - Blocking I/O on reactive threads
               - Missing database indexes (flagged via entity annotations)
               - Loading large collections without pagination (Pageable)
               - Inefficient String operations (use StringBuilder for loops)

            5. CODE QUALITY
               - SOLID principle violations
               - Methods doing more than one thing (violates SRP)
               - Deep nesting — suggest early returns
               - Checked exceptions swallowed silently
               - Missing or misleading Javadoc on public APIs
               - Overly complex lambdas / streams — suggest extracting methods

            6. TESTS
               - Missing unit tests for business logic
               - No @SpringBootTest or @WebMvcTest for controller changes
               - Weak assertions (assertTrue(result != null) vs assertNotNull + assertEquals)
               - No edge-case coverage (empty list, null input, boundary values)

            FORMAT YOUR RESPONSE EXACTLY AS:

            ## Summary
            (2–3 sentence overall assessment of the PR quality and risk level)

            ## 🔴 Critical Issues
            (Bugs and security holes — must fix before merge.
             Include file name and method/line reference where possible.
             Show a corrected code snippet for each issue.)

            ## 🟡 Warnings
            (Important but non-blocking. Spring/JPA concerns, performance risks.)

            ## 🔵 Suggestions
            (Minor improvements, Java idioms, readability, test coverage.)

            ## ✅ What's done well
            (Always include at least one positive observation.)

            RULES:
            - Omit any section that has no items.
            - Use GitHub-flavoured Markdown.
            - For code snippets, always specify the language: ```java
            - Be specific — name the class, method, and line number when possible.
            - Keep total response under 3000 words.
            - If the diff is only test code, focus entirely on test quality.
            """;

    private final RestTemplate restTemplate;
    private final ReviewConfig config;

    /**
     * Sends the filtered diff to Claude and returns the review text.
     *
     * @param filteredDiff the diff content to review
     * @return formatted review as a Markdown string
     */
    public String review(String filteredDiff) {
        log.info("Sending diff to Claude model={} maxTokens={}",
                config.getClaudeModel(), config.getMaxTokens());

        String userContent = String.format(
                "PR: \"%s\" by @%s%n%n```diff%n%s%n```%n%nPlease review this Java/Spring Boot pull request.",
                config.getPrTitle(),
                config.getPrAuthor(),
                filteredDiff
        );

        ClaudeRequest request = ClaudeRequest.builder()
                .model(config.getClaudeModel())
                .maxTokens(config.getMaxTokens())
                .system(SYSTEM_PROMPT)
                .messages(List.of(
                        ClaudeMessage.builder()
                                .role("user")
                                .content(userContent)
                                .build()
                ))
                .build();

        ResponseEntity<ClaudeResponse> response = restTemplate.exchange(
                ANTHROPIC_API_URL,
                HttpMethod.POST,
                new HttpEntity<>(request, buildHeaders()),
                ClaudeResponse.class
        );

        ClaudeResponse body = response.getBody();
        if (body == null || body.getFirstText().isBlank()) {
            throw new IllegalStateException("Claude returned an empty response");
        }

        log.info("Claude review received: {} characters", body.getFirstText().length());
        return body.getFirstText();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", config.getAnthropicApiKey());
        headers.set("anthropic-version", ANTHROPIC_VERSION);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
