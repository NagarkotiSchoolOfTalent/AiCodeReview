package com.yourcompany.codereview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.codereview.config.ReviewConfig;
import com.yourcompany.codereview.model.GitHubCommentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


/**
 * Handles all communication with the GitHub REST API:
 *   - Fetching the PR diff
 *   - Listing existing PR comments
 *   - Deleting old bot review comments
 *   - Posting the new review comment
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubService {

    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String BOT_COMMENT_MARKER = "<!-- claude-java-review -->";

    private final RestTemplate restTemplate;
    private final ReviewConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Diff ─────────────────────────────────────────────────────────────────

    /**
     * Fetches the raw unified diff between the PR's base and head commits.
     */
    public String fetchDiff() {
        String url = String.format(
                "%s/repos/%s/%s/compare/%s...%s",
                GITHUB_API_BASE,
                config.getOwner(),
                config.getRepoName(),
                config.getBaseSha(),
                config.getHeadSha()
        );

        HttpHeaders headers = buildHeaders();
        headers.set(HttpHeaders.ACCEPT, "application/vnd.github.v3.diff");

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class
        );

        log.info("Fetched diff: {} characters", response.getBody() != null ? response.getBody().length() : 0);
        return response.getBody() != null ? response.getBody() : "";
    }

    // ── Comment management ────────────────────────────────────────────────────

    /**
     * Deletes any previous bot review comments so re-pushes get a fresh review.
     */
    public void deleteOldReviewComments() {
        String url = String.format(
                "%s/repos/%s/%s/issues/%s/comments",
                GITHUB_API_BASE,
                config.getOwner(),
                config.getRepoName(),
                config.getPrNumber()
        );

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(buildHeaders()), String.class
        );

        try {
            JsonNode comments = objectMapper.readTree(response.getBody());
            for (JsonNode comment : comments) {
                boolean isBot = "Bot".equals(comment.path("user").path("type").asText());
                boolean hasMarker = comment.path("body").asText().contains(BOT_COMMENT_MARKER);
                if (isBot && hasMarker) {
                    long commentId = comment.path("id").asLong();
                    deleteComment(commentId);
                    log.info("Deleted old review comment id={}", commentId);
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse existing comments, skipping cleanup: {}", e.getMessage());
        }
    }

    private void deleteComment(long commentId) {
        String url = String.format(
                "%s/repos/%s/%s/issues/comments/%d",
                GITHUB_API_BASE,
                config.getOwner(),
                config.getRepoName(),
                commentId
        );
        restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(buildHeaders()), Void.class);
    }

    /**
     * Posts the Claude review as a new PR comment.
     */
    public void postReviewComment(String reviewBody) {
        String url = String.format(
                "%s/repos/%s/%s/issues/%s/comments",
                GITHUB_API_BASE,
                config.getOwner(),
                config.getRepoName(),
                config.getPrNumber()
        );

        String fullBody = BOT_COMMENT_MARKER + "\n"
                + "## \uD83E\uDD16 Claude AI Code Review (Java / Spring Boot)\n\n"
                + reviewBody
                + "\n\n---\n"
                + "<sub>Reviewed by Claude · " + java.time.Instant.now() + "</sub>";

        GitHubCommentRequest request = new GitHubCommentRequest(fullBody);

        restTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(request, buildHeaders()),
                String.class
        );

        log.info("Review comment posted to PR #{}", config.getPrNumber());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + config.getGithubToken());
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return headers;
    }
}
