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
     * Object location: path-style reference {@code {bucket}/{objectKey}} (no scheme/host).
     * Resolved with server-side {@code S3_BASE_URL} / {@code s3.base-url}. Example: {@code my-bucket/2026-05-04/file.txt}.
     * For backward compatibility, a full URL {@code http(s)://{host}/{bucket}/{objectKey}} matching {@code s3.base-url} is still accepted.
     */
    @Schema(description = "Bucket and object key, e.g. my-bucket/path/to/file.pdf (S3_BASE_URL is applied on the server). Legacy: full URL matching s3.base-url.")
    @NotBlank(message = "attachmentUrl is required")
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
