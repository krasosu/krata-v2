package de.krata.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexJobStatus {

    public enum Status { PENDING, INDEXED, SKIPPED, FAILED }

    private String attachmentUuid;
    private Status status;
    private Boolean indexed;  // nur bei INDEXED/SKIPPED
    private String errorMessage;  // bei FAILED
}
