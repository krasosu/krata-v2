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
public class SearchResultHit {

    private String attachmentUuid;
    /** Snippet mit hervorgehobenen Suchbegriffen (nur wenn angefragt und Content gespeichert). */
    private String snippet;
}
