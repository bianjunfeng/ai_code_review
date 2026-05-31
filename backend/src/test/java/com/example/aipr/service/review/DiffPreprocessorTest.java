package com.example.aipr.service.review;

import com.example.aipr.config.ReviewProperties;
import com.example.aipr.service.github.GitHubChangedFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffPreprocessorTest {

    @Test
    void preprocess_truncatesSingleFilePatchAndRecordsLengths() {
        DiffPreprocessor preprocessor = new DiffPreprocessor(properties(10, 8, 100));

        List<DiffPreprocessor.PreparedFile> result = preprocessor.preprocess(List.of(file("src/main/java/App.java", "123456789012")));

        DiffPreprocessor.PreparedFile prepared = result.get(0);
        assertFalse(prepared.skipped());
        assertTrue(prepared.truncated());
        assertEquals(12, prepared.originalPatchLength());
        assertEquals(8, prepared.analyzedPatchLength());
        assertEquals("12345678", prepared.analyzedPatch());
    }

    @Test
    void preprocess_skipsLowerPriorityFilesAfterTotalPatchLimit() {
        DiffPreprocessor preprocessor = new DiffPreprocessor(properties(10, 8, 8));

        List<DiffPreprocessor.PreparedFile> result = preprocessor.preprocess(List.of(
                file("README.md", "12345678"),
                file("src/main/java/App.java", "abcdefgh")
        ));

        assertTrue(result.get(0).skipped());
        assertEquals(DiffPreprocessor.REASON_TOTAL_PATCH_LIMIT, result.get(0).skipReason());
        assertFalse(result.get(1).skipped());
        assertEquals("abcdefgh", result.get(1).analyzedPatch());
    }

    @Test
    void preprocess_skipsFilesAfterMaxFilesLimit() {
        DiffPreprocessor preprocessor = new DiffPreprocessor(properties(1, 100, 1000));

        List<DiffPreprocessor.PreparedFile> result = preprocessor.preprocess(List.of(
                file("src/main/java/App.java", "patch-1"),
                file("src/main/java/UserService.java", "patch-2")
        ));

        assertFalse(result.get(0).skipped());
        assertTrue(result.get(1).skipped());
        assertEquals(DiffPreprocessor.REASON_FILE_COUNT_LIMIT, result.get(1).skipReason());
    }

    private ReviewProperties properties(int maxFiles, int maxFilePatchChars, int maxTotalPatchChars) {
        ReviewProperties properties = new ReviewProperties();
        properties.getDiff().setMaxFiles(maxFiles);
        properties.getDiff().setMaxFilePatchChars(maxFilePatchChars);
        properties.getDiff().setMaxTotalPatchChars(maxTotalPatchChars);
        return properties;
    }

    private GitHubChangedFile file(String filename, String patch) {
        return GitHubChangedFile.builder()
                .filename(filename)
                .status("modified")
                .additions(1)
                .deletions(0)
                .changes(1)
                .patch(patch)
                .build();
    }
}
