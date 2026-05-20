package com.yourcompany.codereview.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// ─────────────────────────────────────────────────────────────────────────────
// GitHub API Models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents a GitHub issue/PR comment returned by the list-comments API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
class GitHubComment {
    private Long id;
    private String body;

    @JsonProperty("user")
    private GitHubUser user;
}

/**
 * GitHub user object embedded in comments.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
class GitHubUser {
    private String login;
    private String type; // "Bot" or "User"
}

// ─────────────────────────────────────────────────────────────────────────────
// Anthropic Claude API Models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single content block in Claude's response (type="text" most commonly).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
class ClaudeContentBlock {
    private String type;
    private String text;
}
