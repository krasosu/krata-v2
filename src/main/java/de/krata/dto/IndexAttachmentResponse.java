package de.krata.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Indexing response: whether the object was indexed or skipped (e.g. audio/video).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexAttachmentResponse {

    /** Record UUID (echoed from request). */
    private String recordUuid;

    /** Attachment UUID (echoed from request). */
    private String attachmentUuid;

    /** True if the attachment was indexed into Lucene. */
    private boolean indexed;

    /** Reason when not indexed (e.g. "content_type_not_indexable"). */
    private String skippedReason;
}
