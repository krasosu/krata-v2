package de.krata.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchIndexRequest {

    @Valid
    @NotEmpty(message = "Mindestens ein Attachment erforderlich")
    @Size(max = 1000, message = "Max. 1000 Attachments pro Batch")
    private List<IndexAttachmentRequest> attachments;
}
