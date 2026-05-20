package com.yourcompany.codereview.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for creating a GitHub PR comment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubCommentRequest {
    private String body;
}
