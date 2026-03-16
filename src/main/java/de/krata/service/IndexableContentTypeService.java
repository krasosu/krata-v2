package de.krata.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Entscheidet, ob ein Attachment für die Lucene-Volltextsuche indiziert werden soll.
 * Audio, Video und reine Bildformate werden übersprungen (kein sinnvoller Volltext).
 */
@Service
@Slf4j
public class IndexableContentTypeService {

    /**
     * MIME-Typen bzw. -Präfixe, die nicht indiziert werden (z.B. Audio, Video).
     * Alles andere (Dokumente, Text) wird indiziert.
     */
    private static final Set<String> NON_INDEXABLE_PREFIXES = Set.of(
            "audio/",
            "video/",
            "image/"
    );

    /**
     * Prüft, ob der gegebene MIME-Type für eine Lucene-Indizierung geeignet ist.
     *
     * @param mimeType von Tika erkannter Content-Type (z.B. "application/pdf", "audio/mpeg")
     * @return true wenn indiziert werden soll, false z.B. für Audio/Video/Bilder
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
