package de.krata.service;

import de.krata.dto.IndexAttachmentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachmentIndexServiceTest {

    private AttachmentDownloadService downloadService;
    private TextExtractionService textExtractionService;
    private LuceneIndexService luceneIndexService;
    private IndexableContentTypeService indexableContentTypeService;
    private AttachmentIndexService service;

    @BeforeEach
    void setUp() {
        downloadService = mock(AttachmentDownloadService.class);
        textExtractionService = mock(TextExtractionService.class);
        luceneIndexService = mock(LuceneIndexService.class);
        indexableContentTypeService = mock(IndexableContentTypeService.class);
        service = new AttachmentIndexService(
                downloadService, textExtractionService, luceneIndexService, indexableContentTypeService);
    }

    @Test
    @DisplayName("indexFromUrl: indexable content is downloaded, extracted and pushed to Lucene")
    void indexableAttachmentIsIndexed() throws Exception {
        /* ARRANGE */
        byte[] payload = "hello".getBytes();
        when(downloadService.downloadAsBytes("bucket/file.txt")).thenReturn(payload);
        when(downloadService.parseS3Url("bucket/file.txt"))
                .thenReturn(new AttachmentDownloadService.S3Location("http://s3", "bucket", "file.txt"));
        when(textExtractionService.detectContentType(payload, "file.txt")).thenReturn("text/plain");
        when(indexableContentTypeService.isIndexable("text/plain")).thenReturn(true);
        when(textExtractionService.extractText(payload, "file.txt")).thenReturn("hello");

        /* ACT */
        IndexAttachmentResponse response = service.indexFromUrl("bucket/file.txt", "att-1", "rec-1", null);

        /* ASSERT */
        assertThat(response.isIndexed()).isTrue();
        assertThat(response.getAttachmentUuid()).isEqualTo("att-1");
        assertThat(response.getRecordUuid()).isEqualTo("rec-1");
        verify(luceneIndexService).indexDocument(eq("rec-1"), eq("att-1"), eq("hello"), any());
    }

    @Test
    @DisplayName("indexFromUrl: non-indexable mime type is skipped without touching Lucene")
    void nonIndexableContentTypeIsSkipped() throws Exception {
        /* ARRANGE */
        byte[] payload = new byte[]{1, 2, 3};
        when(downloadService.downloadAsBytes("bucket/song.mp3")).thenReturn(payload);
        when(downloadService.parseS3Url("bucket/song.mp3"))
                .thenReturn(new AttachmentDownloadService.S3Location("http://s3", "bucket", "song.mp3"));
        when(textExtractionService.detectContentType(payload, "song.mp3")).thenReturn("audio/mpeg");
        when(indexableContentTypeService.isIndexable("audio/mpeg")).thenReturn(false);

        /* ACT */
        IndexAttachmentResponse response = service.indexFromUrl(
                "bucket/song.mp3", "att-2", "rec-1", Instant.parse("2026-05-01T00:00:00Z"));

        /* ASSERT */
        assertThat(response.isIndexed()).isFalse();
        assertThat(response.getSkippedReason()).isEqualTo("content_type_not_indexable");
        verify(luceneIndexService, never()).indexDocument(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("indexFromUrl: indexable but blank text still indexes (warning is logged)")
    void indexableButEmptyExtractionStillIndexes() throws Exception {
        /* ARRANGE */
        byte[] payload = new byte[]{0};
        when(downloadService.downloadAsBytes("bucket/blank.pdf")).thenReturn(payload);
        when(downloadService.parseS3Url("bucket/blank.pdf"))
                .thenReturn(new AttachmentDownloadService.S3Location("http://s3", "bucket", "blank.pdf"));
        when(textExtractionService.detectContentType(payload, "blank.pdf")).thenReturn("application/pdf");
        when(indexableContentTypeService.isIndexable("application/pdf")).thenReturn(true);
        when(textExtractionService.extractText(payload, "blank.pdf")).thenReturn("   ");

        /* ACT */
        IndexAttachmentResponse response = service.indexFromUrl("bucket/blank.pdf", "att-3", "rec-1", null);

        /* ASSERT */
        assertThat(response.isIndexed()).isTrue();
        verify(luceneIndexService).indexDocument(eq("rec-1"), eq("att-3"), eq("   "), any());
    }
}
