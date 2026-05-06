package de.krata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class SearchRequest {

    @NotBlank(message = "query ist erforderlich")
    private String query;

    @Builder.Default
    @Min(0)
    private int from = 0;

    @Builder.Default
    @Min(1)
    @Max(500)
    private int size = 20;

    @Builder.Default
    private boolean withHighlight = false;

    /**
     * Lower bound for the user-provided document creation timestamp (document_created_at), inclusive.
     */
    @Schema(description = "Nur Dokumente mit Anwender-Erstellungszeit >= diesem Zeitpunkt (ISO-8601), optional")
    private Instant createdFrom;

    /**
     * Upper bound for the user-provided document creation timestamp (document_created_at), inclusive.
     */
    @Schema(description = "Nur Dokumente mit Anwender-Erstellungszeit <= diesem Zeitpunkt (ISO-8601), optional")
    private Instant createdTo;

    @AssertTrue(message = "createdFrom muss vor oder gleich createdTo sein")
    public boolean isValidCreatedWindow() {
        if (createdFrom == null || createdTo == null) {
            return true;
        }
        return !createdFrom.isAfter(createdTo);
    }
}
