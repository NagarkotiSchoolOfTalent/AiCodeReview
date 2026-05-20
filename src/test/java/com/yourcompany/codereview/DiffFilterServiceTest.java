package com.yourcompany.codereview;

import com.yourcompany.codereview.config.ReviewConfig;
import com.yourcompany.codereview.service.DiffFilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiffFilterServiceTest {

    private DiffFilterService service;

    @BeforeEach
    void setUp() {
        ReviewConfig config = new ReviewConfig();
        config.setMaxDiffChars(60_000);
        service = new DiffFilterService(config);
    }

    @Test
    void shouldKeepJavaFiles() {
        String diff = """
                diff --git a/src/main/java/com/example/UserService.java b/src/main/java/com/example/UserService.java
                index abc..def 100644
                --- a/UserService.java
                +++ b/UserService.java
                @@ -1,5 +1,6 @@
                +import lombok.extern.slf4j.Slf4j;
                """;
        String result = service.filter(diff);
        assertThat(result).contains("UserService.java");
    }

    @Test
    void shouldSkipJarFiles() {
        String diff = "diff --git a/libs/some-lib.jar b/libs/some-lib.jar\nbinary content";
        String result = service.filter(diff);
        assertThat(result).doesNotContain("some-lib.jar");
    }

    @Test
    void shouldSkipImageFiles() {
        String diff = "diff --git a/src/main/resources/logo.png b/src/main/resources/logo.png\nbinary";
        String result = service.filter(diff);
        assertThat(result).doesNotContain("logo.png");
    }

    @Test
    void shouldTruncateLargeDiffs() {
        ReviewConfig config = new ReviewConfig();
        config.setMaxDiffChars(100);
        DiffFilterService smallLimitService = new DiffFilterService(config);

        String diff = "diff --git a/Main.java b/Main.java\n" + "x".repeat(200);
        String result = smallLimitService.filter(diff);

        assertThat(result.length()).isLessThanOrEqualTo(200);
        assertThat(result).contains("truncated");
    }

    @Test
    void shouldReturnEmptyForBlankDiff() {
        assertThat(service.filter("")).isBlank();
        assertThat(service.filter(null)).isBlank();
    }
}
