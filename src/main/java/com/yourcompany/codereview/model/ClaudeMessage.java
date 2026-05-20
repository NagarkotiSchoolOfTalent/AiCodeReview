package com.yourcompany.codereview.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single message in the Claude conversation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaudeMessage {
    private String role;   // "user" or "assistant"
    private String content;
}
