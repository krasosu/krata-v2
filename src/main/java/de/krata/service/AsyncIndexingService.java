package de.krata.service;

import de.krata.dto.IndexAttachmentResponse;
import de.krata.dto.IndexJobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * Asynchrone Indizierung für hohen Durchsatz (50k+/Tag).
 * Nutzt IndexingQueueBackend (In-Memory oder Redis); Redis übersteht Neustarts.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncIndexingService {

    private final AttachmentIndexService attachmentIndexService;
    private final IndexingQueueBackend backend;

    @Value("${krata.indexing.threads:8}")
    private int workerThreads;

    @Value("${krata.indexing.poll-timeout-sec:5}")
    private int pollTimeoutSec;

    private ExecutorService executor;

    @PostConstruct
    public void init() {
        executor = Executors.newFixedThreadPool(workerThreads);
        for (int i = 0; i < workerThreads; i++) {
            executor.submit(this::worker);
        }
        log.info("Async-Indexierung gestartet: threads={}, backend={}", workerThreads, backend.getClass().getSimpleName());
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean submit(String attachmentUrl, String attachmentUuid) {
        return backend.submit(new IndexTask(attachmentUrl, attachmentUuid));
    }

    public int submitBatch(List<IndexTask> tasks) {
        int accepted = 0;
        for (IndexTask task : tasks) {
            if (backend.submit(task)) accepted++;
        }
        return accepted;
    }

    public Optional<IndexJobStatus> getStatus(String attachmentUuid) {
        return backend.getStatus(attachmentUuid);
    }

    public long getQueueSize() {
        return backend.getQueueSize();
    }

    private void worker() {
        while (true) {
            try {
                Optional<IndexTask> taskOpt = backend.takeBlocking(pollTimeoutSec);
                taskOpt.ifPresent(this::process);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Worker-Fehler: {}", e.getMessage());
            }
        }
    }

    private void process(IndexTask task) {
        try {
            IndexAttachmentResponse response = attachmentIndexService.indexFromUrl(task.attachmentUrl(), task.attachmentUuid());
            backend.setStatus(task.attachmentUuid(), IndexJobStatus.builder()
                    .attachmentUuid(task.attachmentUuid())
                    .status(response.isIndexed() ? IndexJobStatus.Status.INDEXED : IndexJobStatus.Status.SKIPPED)
                    .indexed(response.isIndexed())
                    .build());
        } catch (Exception e) {
            log.warn("Indizierung fehlgeschlagen uuid={}: {}", task.attachmentUuid(), e.getMessage());
            backend.setStatus(task.attachmentUuid(), IndexJobStatus.builder()
                    .attachmentUuid(task.attachmentUuid())
                    .status(IndexJobStatus.Status.FAILED)
                    .errorMessage(e.getMessage())
                    .build());
        }
    }

    public record IndexTask(String attachmentUrl, String attachmentUuid) {}
}
