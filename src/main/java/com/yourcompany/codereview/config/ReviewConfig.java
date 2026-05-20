package com.yourcompany.codereview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds environment variables (set by GitHub Actions) into typed properties.
 * All fields are populated from application.yml which reads from env vars.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "review")
public class ReviewConfig {

    /** Anthropic API key - from ANTHROPIC_API_KEY env var */
    private String anthropicApiKey;

    /** GitHub personal access token - from GITHUB_TOKEN env var */
    private String githubToken;

    /** PR number to review - from PR_NUMBER env var */
    private String prNumber;

    /** Repository in format owner/repo - from REPO env var */
    private String repo;

    /** Base commit SHA for the diff - from BASE_SHA env var */
    private String baseSha;

    /** Head commit SHA for the diff - from HEAD_SHA env var */
    private String headSha;

    /** Title of the PR - from PR_TITLE env var */
    private String prTitle;

    /** GitHub username of PR author - from PR_AUTHOR env var */
    private String prAuthor;

    /** Claude model to use */
    private String claudeModel;

    /** Maximum tokens in Claude's response */
    private int maxTokens;

    /** Max diff size in characters before truncation */
    private int maxDiffChars;

    /**
     * Returns the repository owner extracted from "owner/repo" format.
     */
    public String getOwner() {
        return repo.split("/")[0];
    }

    /**
     * Returns just the repository name from "owner/repo" format.
     */
    public String getRepoName() {
        return repo.split("/")[1];
    }
}
