package de.krata.health;

import de.krata.service.LuceneIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Prüft, ob der Lucene-Index lesbar ist.
 */
@Component
@RequiredArgsConstructor
public class LuceneHealthIndicator implements HealthIndicator {

    private final LuceneIndexService luceneIndexService;

    @Override
    public Health health() {
        try {
            var result = luceneIndexService.search("content:__health__", 0, 1, false);
            return Health.up()
                    .withDetail("indexReadable", true)
                    .withDetail("totalHits", result.getTotal())
                    .build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}
