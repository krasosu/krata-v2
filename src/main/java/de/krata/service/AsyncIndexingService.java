package de.krata.service;

import de.krata.dto.IndexAttachmentResponse;
import de.krata.dto.IndexJobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * Asynchrone Indizierung für hohen Durchsatz (50k+/Tag).
 * Queue mit Worker-Pool; Status pro attachment_uuid (best-effort, begrenzte Kapazität).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncIndexingService {

    private final AttachmentIndexService attachmentIndexService;

    @Value("${krata.indexing.queue-size:100000}")
    private int queueSize;

    @Value("${krata.indexing.threads:8}")
    private int workerThreads;

    @Value("${krata.indexing.status-max-entries:50000}")
    private int statusMaxEntries;

    private BlockingQueue<IndexTask> queue;
    private ExecutorService executor;
    private final Map<String, IndexJobStatus> statusMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        queue = new LinkedBlockingQueue<>(queueSize);
        executor = Executors.newFixedThreadPool(workerThreads);
        for (int i = 0; i < workerThreads; i++) {
            executor.submit(this::worker);
        }
        log.info("Async-Indexierung gestartet: queueSize={}, threads={}", queueSize, workerThreads);
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

    /**
     * Stellt ein einzelnes Attachment in die Queue. Nicht blockierend.
     * @return true wenn angenommen, false wenn Queue voll
     */
    public boolean submit(String attachmentUrl, String attachmentUuid) {
        evictStatusIfNeeded();
        if (!queue.offer(new IndexTask(attachmentUrl, attachmentUuid))) {
            return false;
        }
        statusMap.put(attachmentUuid, IndexJobStatus.builder()
                .attachmentUuid(attachmentUuid)
                .status(IndexJobStatus.Status.PENDING)
                .build());
        return true;
    }

    /**
     * Stellt mehrere Attachments in die Queue.
     * @return Anzahl angenommener Jobs (kann kleiner als requests sein bei voller Queue)
     */
    public int submitBatch(List<IndexTask> tasks) {
        int accepted = 0;
        for (IndexTask task : tasks) {
            if (submit(task.attachmentUrl(), task.attachmentUuid())) {
                accepted++;
            }
        }
        return accepted;
    }

    public Optional<IndexJobStatus> getStatus(String attachmentUuid) {
        return Optional.ofNullable(statusMap.get(attachmentUuid));
    }

    public int getQueueSize() {
        return queue.size();
    }

    private void worker() {
        while (true) {
            try {
                IndexTask task = queue.poll(5, TimeUnit.SECONDS);
                if (task == null) continue;
                process(task);
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
            statusMap.put(task.attachmentUuid(), IndexJobStatus.builder()
                    .attachmentUuid(task.attachmentUuid())
                    .status(response.isIndexed() ? IndexJobStatus.Status.INDEXED : IndexJobStatus.Status.SKIPPED)
                    .indexed(response.isIndexed())
                    .build());
        } catch (Exception e) {
            log.warn("Indizierung fehlgeschlagen uuid={}: {}", task.attachmentUuid(), e.getMessage());
            statusMap.put(task.attachmentUuid(), IndexJobStatus.builder()
                    .attachmentUuid(task.attachmentUuid())
                    .status(IndexJobStatus.Status.FAILED)
                    .errorMessage(e.getMessage())
                    .build());
        }
    }

    private void evictStatusIfNeeded() {
        if (statusMap.size() >= statusMaxEntries) {
            synchronized (statusMap) {
                if (statusMap.size() >= statusMaxEntries) {
                    statusMap.keySet().stream().limit(statusMaxEntries / 2).toList().forEach(statusMap::remove);
                }
            }
        }
    }

    public record IndexTask(String attachmentUrl, String attachmentUuid) {}
}
