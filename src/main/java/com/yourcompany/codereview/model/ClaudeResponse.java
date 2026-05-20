package com.yourcompany.codereview.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Top-level response from the Anthropic API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClaudeResponse {
    private List<ClaudeContentBlock> content;
    private String id;
    private String model;

    @JsonProperty("stop_reason")
    private String stopReason;

    /**
     * Convenience method to extract the text from the first content block.
     */
    public String getFirstText() {
        if (content == null || content.isEmpty()) return "";
        return content.stream()
                .filter(b -> "text".equals(b.getType()))
                .findFirst()
                .map(ClaudeContentBlock::getText)
                .orElse("");
    }
}
