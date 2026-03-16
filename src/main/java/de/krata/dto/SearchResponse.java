package de.krata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

    /**
     * Liste der attachment_uuids, deren Inhalt auf die Query getroffen hat
     */
    private List<String> attachmentUuids;
}
