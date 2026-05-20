package com.yourcompany.codereview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the full code review pipeline:
 *
 *   Step 1: Fetch the PR diff from GitHub
 *   Step 2: Filter out binary/noise files
 *   Step 3: Send filtered diff to Claude AI
 *   Step 4: Delete any previous bot review comments
 *   Step 5: Post the new review as a PR comment
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeReviewOrchestrator {

    private final GitHubService gitHubService;
    private final DiffFilterService diffFilterService;
    private final ClaudeReviewService claudeReviewService;

    /**
     * Executes the complete review pipeline end-to-end.
     * Called once on application startup by AppConfig.
     */
    public void runReview() {

        // Step 1 — Fetch raw diff from GitHub Compare API
        log.info("Step 1/5: Fetching PR diff from GitHub...");
        String rawDiff = gitHubService.fetchDiff();

        // Step 2 — Filter out binary/generated files
        log.info("Step 2/5: Filtering diff...");
        String filteredDiff = diffFilterService.filter(rawDiff);

        if (filteredDiff.isBlank()) {
            log.info("No reviewable code changes found (only binaries/generated files). Skipping.");
            return;
        }

        log.info("Filtered diff ready: {} characters", filteredDiff.length());

        // Step 3 — Send to Claude for review
        log.info("Step 3/5: Calling Claude AI for review...");
        String reviewText = claudeReviewService.review(filteredDiff);

        // Step 4 — Clean up previous bot comments (handles re-pushes)
        log.info("Step 4/5: Removing previous review comments...");
        gitHubService.deleteOldReviewComments();

        // Step 5 — Post new review comment
        log.info("Step 5/5: Posting review comment to PR...");
        gitHubService.postReviewComment(reviewText);
    }
}
