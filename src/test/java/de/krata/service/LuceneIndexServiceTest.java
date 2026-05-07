package de.krata.service;

import de.krata.config.LuceneConfig;
import de.krata.dto.PaginatedSearchResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LuceneIndexServiceTest {

    @TempDir
    Path tempDir;

    private LuceneIndexService service;
    private LuceneConfig config;

    @BeforeEach
    void setUp() throws Exception {
        config = new LuceneConfig();
        ReflectionTestUtils.setField(config, "indexPath", tempDir.toString());
        ReflectionTestUtils.setField(config, "maxMergeSizeMb", 5.0);
        ReflectionTestUtils.setField(config, "maxCachedMb", 60.0);
        ReflectionTestUtils.setField(config, "commitIntervalSec", 1);
        ReflectionTestUtils.setField(config, "retentionDays", 30);
        ReflectionTestUtils.setField(config, "storeContentForHighlight", true);
        service = new LuceneIndexService(config);
        service.init();
    }

    @AfterEach
    void tearDown() throws Exception {
        service.close();
    }

    @Test
    @DisplayName("indexDocument: stores a document that can be found by full-text query")
    void indexAndFindDocument() throws Exception {
        /* ARRANGE */
        service.indexDocument("rec-1", "att-1", "the quick brown fox jumps over the lazy dog", null);
        service.commitAndRefresh();

        /* ACT */
        PaginatedSearchResponse response = service.search("content:fox", 0, 10, false, null, null);

        /* ASSERT */
        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getHits()).singleElement()
                .satisfies(hit -> {
                    assertThat(hit.getRecordUuid()).isEqualTo("rec-1");
                    assertThat(hit.getAttachmentUuid()).isEqualTo("att-1");
                    assertThat(hit.getSnippet()).isNull();
                });
    }

    @Test
    @DisplayName("search: returns highlighted snippet when storeContentForHighlight=true")
    void searchWithHighlightProducesSnippet() throws Exception {
        /* ARRANGE */
        service.indexDocument("rec-1", "att-1", "Lucene full text search snippet generation works.", null);
        service.commitAndRefresh();

        /* ACT */
        PaginatedSearchResponse response = service.search("content:snippet", 0, 10, true, null, null);

        /* ASSERT */
        assertThat(response.getHits()).singleElement()
                .satisfies(hit -> assertThat(hit.getSnippet()).contains("<em>snippet</em>"));
    }

    @Test
    @DisplayName("search: filters hits by document_created_at time window")
    void searchWithTimeWindowFiltersByDocumentCreatedAt() throws Exception {
        /* ARRANGE */
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-06-01T00:00:00Z");
        service.indexDocument("rec-1", "att-1", "alpha", t1);
        service.indexDocument("rec-2", "att-2", "alpha", t2);
        service.commitAndRefresh();

        /* ACT */
        PaginatedSearchResponse response = service.search(
                "content:alpha", 0, 10, false,
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"));

        /* ASSERT */
        assertThat(response.getHits()).singleElement()
                .satisfies(hit -> assertThat(hit.getAttachmentUuid()).isEqualTo("att-2"));
    }

    @Test
    @DisplayName("search: pagination returns the requested slice and total")
    void paginationReturnsRequestedSlice() throws Exception {
        /* ARRANGE */
        for (int i = 0; i < 5; i++) {
            service.indexDocument("rec-" + i, "att-" + i, "needle", null);
        }
        service.commitAndRefresh();

        /* ACT */
        PaginatedSearchResponse page = service.search("content:needle", 2, 2, false, null, null);

        /* ASSERT */
        assertThat(page.getTotal()).isEqualTo(5);
        assertThat(page.getHits()).hasSize(2);
        assertThat(page.getFrom()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(2);
    }

    @Test
    void legacySearchReturnsAttachmentUuidsOnly() throws Exception {
        /* ARRANGE */
        service.indexDocument("rec-1", "att-1", "alpha", null);
        service.commitAndRefresh();

        /* ACT */
        List<String> uuids = service.search("content:alpha");

        /* ASSERT */
        assertThat(uuids).containsExactly("att-1");
    }

    @Test
    void deleteByAttachmentUuidRemovesDocument() throws Exception {
        /* ARRANGE */
        service.indexDocument("rec-1", "att-1", "to-be-deleted", null);
        service.commitAndRefresh();

        /* ACT */
        service.deleteByAttachmentUuid("att-1");
        service.commitAndRefresh();

        /* ASSERT */
        PaginatedSearchResponse response = service.search("content:to-be-deleted", 0, 10, false, null, null);
        assertThat(response.getTotal()).isZero();
    }

    @Test
    @DisplayName("bulk delete: removes selected documents and keeps the rest")
    void bulkDeleteRemovesAllGivenDocuments() throws Exception {
        /* ARRANGE */
        service.indexDocument("rec-1", "att-1", "purge", null);
        service.indexDocument("rec-2", "att-2", "purge", null);
        service.indexDocument("rec-3", "att-3", "keep", null);
        service.commitAndRefresh();

        /* ACT */
        service.deleteByAttachmentUuids(List.of("att-1", "att-2"));
        service.commitAndRefresh();

        /* ASSERT */
        PaginatedSearchResponse purged = service.search("content:purge", 0, 10, false, null, null);
        PaginatedSearchResponse kept = service.search("content:keep", 0, 10, false, null, null);
        assertThat(purged.getTotal()).isZero();
        assertThat(kept.getTotal()).isEqualTo(1);
    }

    @Test
    void bulkDeleteWithEmptyOrNullListIsNoop() throws Exception {
        /* ARRANGE & ACT & ASSERT (must not throw) */
        service.deleteByAttachmentUuids(null);
        service.deleteByAttachmentUuids(List.of());
    }

    @Test
    void deleteOlderThanRemovesOldDocuments() throws Exception {
        /* ARRANGE */
        service.indexDocument("rec-1", "att-1", "expire", null);
        service.commitAndRefresh();
        long future = System.currentTimeMillis() + 60_000L;

        /* ACT */
        service.deleteOlderThan(future);
        service.commitAndRefresh();

        /* ASSERT */
        PaginatedSearchResponse response = service.search("content:expire", 0, 10, false, null, null);
        assertThat(response.getTotal()).isZero();
    }

    @Test
    void runRetentionDoesNothingWhenDaysIsZero() {
        /* ARRANGE */
        ReflectionTestUtils.setField(config, "retentionDays", 0);

        /* ACT & ASSERT (must not throw) */
        service.runRetention();
    }

    @Test
    void runRetentionExecutesWhenDaysPositive() throws Exception {
        /* ARRANGE */
        service.indexDocument("rec-1", "att-1", "old", null);
        service.commitAndRefresh();
        ReflectionTestUtils.setField(config, "retentionDays", 1_000_000);

        /* ACT & ASSERT (must not throw) */
        service.runRetention();
    }

    @Test
    void scheduledRetentionDelegatesToRunRetention() {
        /* ACT & ASSERT (must not throw) */
        service.scheduledRetention();
    }

    @Test
    @DisplayName("indexDocument: re-indexing the same uuid replaces the previous content (upsert)")
    void updateDocumentReplacesContent() throws Exception {
        /* ARRANGE */
        service.indexDocument("rec-1", "att-1", "first version", null);
        service.commitAndRefresh();
        service.indexDocument("rec-1", "att-1", "second version", null);
        service.commitAndRefresh();

        /* ACT */
        PaginatedSearchResponse first = service.search("content:first", 0, 10, false, null, null);
        PaginatedSearchResponse second = service.search("content:second", 0, 10, false, null, null);

        /* ASSERT */
        assertThat(first.getTotal()).isZero();
        assertThat(second.getTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("search: snippet stays null when storeContentForHighlight is disabled")
    void searchWithoutHighlightWhenStoredContentDisabled() throws Exception {
        /* ARRANGE */
        ReflectionTestUtils.setField(config, "storeContentForHighlight", false);
        service.close();
        service.init();
        service.indexDocument("rec-1", "att-1", "no highlight stored", null);
        service.commitAndRefresh();

        /* ACT */
        PaginatedSearchResponse response = service.search("content:highlight", 0, 10, true, null, null);

        /* ASSERT */
        assertThat(response.getHits()).singleElement()
                .satisfies(hit -> assertThat(hit.getSnippet()).isNull());
    }
}
