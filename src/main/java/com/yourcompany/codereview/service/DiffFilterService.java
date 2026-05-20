package com.yourcompany.codereview.service;

import com.yourcompany.codereview.config.ReviewConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Filters out noise from the raw diff before it is sent to Claude.
 *
 * Removes:
 *   - Binary files (images, fonts, archives, JARs)
 *   - Lock / generated files (pom.xml diffs, .lock files)
 *   - Truncates to maxDiffChars to avoid excessive API cost
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiffFilterService {

    private final ReviewConfig config;

    /**
     * File extensions to skip entirely — not useful for a code review.
     */
    private static final List<String> SKIP_EXTENSIONS = Arrays.asList(
            // Build artifacts & dependencies
            ".jar", ".war", ".ear", ".class",
            // Images & media
            ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico",
            ".mp4", ".mp3", ".wav",
            // Fonts
            ".woff", ".woff2", ".ttf", ".eot", ".otf",
            // Archives
            ".zip", ".tar", ".gz", ".rar",
            // Documents
            ".pdf",
            // Lock / generated
            ".lock", "package-lock.json", "yarn.lock",
            // IDE & OS
            ".DS_Store", ".iml"
    );

    /**
     * Specific file names to skip (Maven wrapper, Gradle wrapper binaries, etc.)
     */
    private static final List<String> SKIP_FILENAMES = Arrays.asList(
            "gradlew", "gradlew.bat",
            "mvnw", "mvnw.cmd",
            "HELP.md",
            "gradle-wrapper.jar"
    );

    /**
     * Filters the raw diff and returns only reviewable Java/config content.
     *
     * @param rawDiff the full diff string from GitHub Compare API
     * @return filtered and possibly truncated diff string
     */
    public String filter(String rawDiff) {
        if (rawDiff == null || rawDiff.isBlank()) return "";

        // Split into per-file chunks at "diff --git" boundaries
        String[] chunks = rawDiff.split("(?=diff --git )");

        String filtered = Arrays.stream(chunks)
                .filter(this::isReviewable)
                .collect(Collectors.joining());

        log.info("Diff filter: {} → {} files kept",
                chunks.length,
                Arrays.stream(chunks).filter(this::isReviewable).count());

        if (filtered.length() > config.getMaxDiffChars()) {
            log.warn("Diff truncated from {} to {} chars", filtered.length(), config.getMaxDiffChars());
            return filtered.substring(0, config.getMaxDiffChars())
                    + "\n\n[... diff truncated — only first "
                    + config.getMaxDiffChars() + " characters reviewed ...]";
        }

        return filtered;
    }

    /**
     * Returns true if this diff chunk should be included in the review.
     */
    private boolean isReviewable(String chunk) {
        if (chunk.isBlank()) return false;

        // Extract the filename from the first line: "diff --git a/path/to/File.java b/path/to/File.java"
        String firstLine = chunk.split("\n")[0];

        // Check extension
        for (String ext : SKIP_EXTENSIONS) {
            if (firstLine.endsWith(ext)) {
                log.debug("Skipping file (extension {}): {}", ext, firstLine);
                return false;
            }
        }

        // Check specific filenames
        for (String name : SKIP_FILENAMES) {
            if (firstLine.contains("/" + name) || firstLine.contains(" " + name)) {
                log.debug("Skipping file (name {}): {}", name, firstLine);
                return false;
            }
        }

        return true;
    }
}
