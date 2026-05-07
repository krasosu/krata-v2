package de.krata.health;

import de.krata.dto.PaginatedSearchResponse;
import de.krata.service.LuceneIndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LuceneHealthIndicatorTest {

    @Test
    @DisplayName("UP when Lucene search succeeds (index readable)")
    void reportsUpWhenSearchSucceeds() throws Exception {
        /* ARRANGE */
        LuceneIndexService svc = mock(LuceneIndexService.class);
        when(svc.search(anyString(), anyInt(), anyInt(), anyBoolean(), any(), any()))
                .thenReturn(PaginatedSearchResponse.builder().total(0).build());

        /* ACT */
        Health health = new LuceneHealthIndicator(svc).health();

        /* ASSERT */
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("indexReadable", true);
    }

    @Test
    @DisplayName("DOWN when Lucene search throws (e.g. corrupt index)")
    void reportsDownWhenSearchThrows() throws Exception {
        /* ARRANGE */
        LuceneIndexService svc = mock(LuceneIndexService.class);
        when(svc.search(anyString(), anyInt(), anyInt(), anyBoolean(), any(), any()))
                .thenThrow(new RuntimeException("nope"));

        /* ACT */
        Health health = new LuceneHealthIndicator(svc).health();

        /* ASSERT */
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error", "nope");
    }
}
