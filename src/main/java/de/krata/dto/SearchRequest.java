package de.krata.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
