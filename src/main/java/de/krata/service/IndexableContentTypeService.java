package de.krata.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Decides whether an object should be indexed for Lucene full-text search.
 * Audio, video and pure image formats are skipped (no meaningful full-text).
 */
@Service
@Slf4j
public class IndexableContentTypeService {

    /**
     * MIME type prefixes that should not be indexed (e.g. audio, video).
     * Everything else (documents, text) will be indexed.
     */
    private static final Set<String> NON_INDEXABLE_PREFIXES = Set.of(
            "audio/",
            "video/",
            "image/"
    );

    /**
     * Checks whether the given MIME type is suitable for Lucene indexing.
     *
     * @param mimeType content type detected by Tika (e.g. "application/pdf", "audio/mpeg")
     * @return true if it should be indexed, false for audio/video/images
     */
    public boolean isIndexable(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return false;
        }
        String normalized = mimeType.toLowerCase().trim().split(";")[0];
        boolean indexable = NON_INDEXABLE_PREFIXES.stream()
                .noneMatch(normalized::startsWith);
        if (!indexable) {
            log.debug("Content-Type nicht indizierbar (übersprungen): {}", mimeType);
        }
        return indexable;
    }
}
