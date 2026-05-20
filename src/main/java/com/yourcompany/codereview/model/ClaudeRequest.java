package com.yourcompany.codereview.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body sent to the Anthropic /v1/messages endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaudeRequest {

    private String model;

    @JsonProperty("max_tokens")
    private int maxTokens;

    private String system;

    private List<ClaudeMessage> messages;
}
