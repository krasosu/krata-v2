package de.krata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexAttachmentRequest {

    /**
     * Object URL in the S3-compatible storage (path-style: {S3_BASE_URL}/{bucket}/{objectKey}).
     */
    @NotBlank(message = "attachmentUrl ist erforderlich")
    private String attachmentUrl;

    /**
     * Unique attachment ID (stored as metadata in the Lucene index).
     */
    @NotBlank(message = "attachmentUuid ist erforderlich")
    private String attachmentUuid;

    /**
     * Unique record ID (business object) the attachment belongs to.
     */
    @NotBlank(message = "recordUuid ist erforderlich")
    private String recordUuid;

    /**
     * User-provided creation timestamp (ISO-8601).
     * If omitted, the indexing timestamp is used.
     */
    @Schema(description = "Optional: Anwender-Erstellungszeitpunkt (ISO-8601). Ohne Angabe: Zeitpunkt der Indizierung.")
    private Instant documentCreatedAt;
}
