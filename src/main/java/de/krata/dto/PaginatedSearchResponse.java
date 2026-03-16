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
public class PaginatedSearchResponse {

    private long total;
    private int from;
    private int size;
    private List<SearchResultHit> hits;
}
