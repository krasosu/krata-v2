package de.krata.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class LuceneConfigTest {

    @Test
    void gettersExposeConfiguredValues() {
        /* ARRANGE */
        LuceneConfig config = new LuceneConfig();
        ReflectionTestUtils.setField(config, "indexPath", "./tmp/index");
        ReflectionTestUtils.setField(config, "maxMergeSizeMb", 7.5);
        ReflectionTestUtils.setField(config, "maxCachedMb", 128.0);
        ReflectionTestUtils.setField(config, "commitIntervalSec", 9);
        ReflectionTestUtils.setField(config, "retentionDays", 14);
        ReflectionTestUtils.setField(config, "storeContentForHighlight", true);

        /* ACT & ASSERT */
        assertThat(config.getIndexPath().toString()).endsWith("tmp/index");
        assertThat(config.getMaxMergeSizeMb()).isEqualTo(7.5);
        assertThat(config.getMaxCachedMb()).isEqualTo(128.0);
        assertThat(config.getCommitIntervalSec()).isEqualTo(9);
        assertThat(config.getRetentionDays()).isEqualTo(14);
        assertThat(config.isStoreContentForHighlight()).isTrue();
    }
}
