package de.krata.service;

import de.krata.dto.IndexJobStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.*;

@Component
@Slf4j
@ConditionalOnProperty(name = "krata.indexing.use-redis", havingValue = "false", matchIfMissing = true)
public class InMemoryIndexingBackend implements IndexingQueueBackend {

    @Value("${krata.indexing.queue-size:100000}")
    private int queueSize;

    @Value("${krata.indexing.status-max-entries:50000}")
    private int statusMaxEntries;

    private BlockingQueue<AsyncIndexingService.IndexTask> queue;
    private final ConcurrentHashMap<String, IndexJobStatus> statusMap = new ConcurrentHashMap<>();

    @jakarta.annotation.PostConstruct
    public void init() {
        this.queue = new LinkedBlockingQueue<>(queueSize);
    }

    @Override
    public boolean submit(AsyncIndexingService.IndexTask task) {
        evictStatusIfNeeded();
        if (!queue.offer(task)) return false;
        statusMap.put(task.attachmentUuid(), IndexJobStatus.builder()
                .attachmentUuid(task.attachmentUuid())
                .status(IndexJobStatus.Status.PENDING)
                .build());
        return true;
    }

    @Override
    public Optional<AsyncIndexingService.IndexTask> takeBlocking(int timeoutSeconds) throws InterruptedException {
        AsyncIndexingService.IndexTask task = queue.poll(timeoutSeconds, TimeUnit.SECONDS);
        return Optional.ofNullable(task);
    }

    @Override
    public void setStatus(String attachmentUuid, IndexJobStatus status) {
        statusMap.put(attachmentUuid, status);
    }

    @Override
    public Optional<IndexJobStatus> getStatus(String attachmentUuid) {
        return Optional.ofNullable(statusMap.get(attachmentUuid));
    }

    @Override
    public long getQueueSize() {
        return queue.size();
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
}
