package de.krata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
