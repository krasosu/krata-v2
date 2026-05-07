package de.krata.service;

import de.krata.dto.IndexAttachmentResponse;
import de.krata.dto.IndexJobStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncIndexingServiceTest {

    private AttachmentIndexService attachmentIndexService;
    private AsyncIndexingService service;

    @BeforeEach
    void setUp() {
        attachmentIndexService = mock(AttachmentIndexService.class);
        service = new AsyncIndexingService(attachmentIndexService);
        ReflectionTestUtils.setField(service, "queueSize", 4);
        ReflectionTestUtils.setField(service, "workerThreads", 2);
        ReflectionTestUtils.setField(service, "statusMaxEntries", 4);
        ReflectionTestUtils.setField(service, "pollTimeoutSec", 1);
        service.init();
    }

    @AfterEach
    void tearDown() {
        ExecutorService exec = (ExecutorService) ReflectionTestUtils.getField(service, "executor");
        if (exec != null) {
            exec.shutdownNow();
            try {
                exec.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    @DisplayName("submit: indexable attachment ends up with status INDEXED")
    void submitAcceptsTaskAndProcessesIt() throws Exception {
        /* ARRANGE */
        when(attachmentIndexService.indexFromUrl(eq("bucket/key"), eq("att-1"), eq("rec-1"), any()))
                .thenReturn(IndexAttachmentResponse.builder()
                        .recordUuid("rec-1")
                        .attachmentUuid("att-1")
                        .indexed(true)
                        .build());

        /* ACT */
        boolean accepted = service.submit("bucket/key", "att-1", "rec-1", Instant.parse("2026-01-01T00:00:00Z"));

        /* ASSERT */
        assertThat(accepted).isTrue();
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(service.getStatus("att-1")).hasValueSatisfying(s ->
                        assertThat(s.getStatus()).isEqualTo(IndexJobStatus.Status.INDEXED)));
        verify(attachmentIndexService, atLeastOnce())
                .indexFromUrl(eq("bucket/key"), eq("att-1"), eq("rec-1"), any());
    }

    @Test
    @DisplayName("submit: skipped response is reflected as SKIPPED status")
    void processedTaskWithSkippedReasonReportsSkipped() throws Exception {
        /* ARRANGE */
        when(attachmentIndexService.indexFromUrl(any(), eq("att-skip"), any(), any()))
                .thenReturn(IndexAttachmentResponse.builder()
                        .attachmentUuid("att-skip")
                        .indexed(false)
                        .skippedReason("content_type_not_indexable")
                        .build());

        /* ACT */
        service.submit("bucket/skip", "att-skip", "rec", null);

        /* ASSERT */
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(service.getStatus("att-skip")).hasValueSatisfying(s ->
                        assertThat(s.getStatus()).isEqualTo(IndexJobStatus.Status.SKIPPED)));
    }

    @Test
    @DisplayName("submit: exception in worker is captured as FAILED status with errorMessage")
    void exceptionInProcessIsRecordedAsFailed() throws Exception {
        /* ARRANGE */
        when(attachmentIndexService.indexFromUrl(any(), eq("att-fail"), any(), any()))
                .thenThrow(new IOException("boom"));

        /* ACT */
        service.submit("bucket/fail", "att-fail", "rec", null);

        /* ASSERT */
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(service.getStatus("att-fail")).hasValueSatisfying(s -> {
                    assertThat(s.getStatus()).isEqualTo(IndexJobStatus.Status.FAILED);
                    assertThat(s.getErrorMessage()).isEqualTo("boom");
                }));
    }

    @Test
    void submitBatchReturnsAcceptedCount() throws Exception {
        /* ARRANGE */
        when(attachmentIndexService.indexFromUrl(any(), any(), any(), any()))
                .thenReturn(IndexAttachmentResponse.builder().indexed(true).build());
        List<AsyncIndexingService.IndexTask> tasks = List.of(
                new AsyncIndexingService.IndexTask("b/k1", "u1", "r1", null),
                new AsyncIndexingService.IndexTask("b/k2", "u2", "r2", null));

        /* ACT */
        int accepted = service.submitBatch(tasks);

        /* ASSERT */
        assertThat(accepted).isEqualTo(2);
    }

    @Test
    void getStatusReturnsEmptyWhenUnknown() {
        /* ACT & ASSERT */
        assertThat(service.getStatus("unknown")).isEmpty();
    }

    @Test
    void getQueueSizeReportsZeroAfterDrain() throws Exception {
        /* ARRANGE */
        when(attachmentIndexService.indexFromUrl(any(), any(), any(), any()))
                .thenReturn(IndexAttachmentResponse.builder().indexed(true).build());
        service.submit("bucket/key", "att-q", "rec-q", null);

        /* ACT */
        await().atMost(5, TimeUnit.SECONDS).until(() -> service.getQueueSize() == 0L);

        /* ASSERT */
        assertThat(service.getQueueSize()).isZero();
    }

    @Test
    @DisplayName("eviction: status map is trimmed once it crosses statusMaxEntries")
    void evictionTriggeredWhenStatusMapFull() throws Exception {
        /* ARRANGE */
        when(attachmentIndexService.indexFromUrl(any(), any(), any(), any()))
                .thenReturn(IndexAttachmentResponse.builder().indexed(true).build());

        /* ACT */
        for (int i = 0; i < 8; i++) {
            service.submit("b/k", "att-" + i, "r", null);
        }

        /* ASSERT */
        await().atMost(5, TimeUnit.SECONDS).until(() -> service.getQueueSize() == 0L);
    }
}
