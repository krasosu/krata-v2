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
     * URL des Attachments im S3/MinIO Storage (z.B. http://localhost:9000/attachments/uuid/datei.pdf)
     */
    @NotBlank(message = "attachmentUrl ist erforderlich")
    private String attachmentUrl;

    /**
     * Eindeutige ID des Attachments (wird als Metadatum im Lucene-Index gespeichert)
     */
    @NotBlank(message = "attachmentUuid ist erforderlich")
    private String attachmentUuid;

    /**
     * Ursprünglicher Erstellungszeitpunkt durch den Anwender (ISO-8601).
     * Ohne Angabe wird bei der Indizierung der aktuelle Zeitpunkt verwendet (entspricht dann typischerweise der Indizierungszeit).
     */
    @Schema(description = "Optional: Anwender-Erstellungszeitpunkt (ISO-8601). Ohne Angabe: Zeitpunkt der Indizierung.")
    private Instant documentCreatedAt;
}
