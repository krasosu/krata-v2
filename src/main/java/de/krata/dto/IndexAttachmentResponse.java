package de.krata.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Antwort nach Indizierungsanfrage: ob indiziert wurde oder übersprungen (z.B. Audio/Video).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexAttachmentResponse {

    /** true wenn das Attachment in Lucene indiziert wurde */
    private boolean indexed;

    /** Grund, wenn nicht indiziert (z.B. "content_type_not_indexable") */
    private String skippedReason;
}
