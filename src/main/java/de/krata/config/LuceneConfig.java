package de.krata.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class LuceneConfig {

    @Value("${lucene.index-path:./data/lucene-index}")
    private String indexPath;

    @Value("${lucene.cache.max-merge-size-mb:5.0}")
    private double maxMergeSizeMb;

    @Value("${lucene.cache.max-cached-mb:60.0}")
    private double maxCachedMb;

    /** Commit-Intervall (Sekunden) – Batches für hohen Durchsatz. */
    @Value("${lucene.commit-interval-sec:3}")
    private int commitIntervalSec;

    /** Retention: Dokumente älter als diese Anzahl Tage werden gelöscht (0 = deaktiviert). */
    @Value("${lucene.retention-days:30}")
    private int retentionDays;

    /** Content speichern für Snippet-Highlighting (erhöht Speicherbedarf). */
    @Value("${lucene.store-content-for-highlight:false}")
    private boolean storeContentForHighlight;

    public Path getIndexPath() {
        return Paths.get(indexPath).toAbsolutePath();
    }

    public double getMaxMergeSizeMb() {
        return maxMergeSizeMb;
    }

    public double getMaxCachedMb() {
        return maxCachedMb;
    }

    public int getCommitIntervalSec() {
        return commitIntervalSec;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public boolean isStoreContentForHighlight() {
        return storeContentForHighlight;
    }
}
