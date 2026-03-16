package de.krata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.krata.dto.IndexJobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "krata.indexing.use-redis", havingValue = "true")
public class RedisIndexingBackend implements IndexingQueueBackend {

    private static final String QUEUE_KEY = "krata:index:queue";
    private static final String STATUS_KEY_PREFIX = "krata:index:status:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${krata.indexing.redis.status-ttl-hours:24}")
    private int statusTtlHours;

    @Override
    public boolean submit(AsyncIndexingService.IndexTask task) {
        try {
            String json = objectMapper.writeValueAsString(new TaskDto(task.attachmentUrl(), task.attachmentUuid()));
            redis.opsForList().leftPush(QUEUE_KEY, json);
            setStatus(task.attachmentUuid(), IndexJobStatus.builder()
                    .attachmentUuid(task.attachmentUuid())
                    .status(IndexJobStatus.Status.PENDING)
                    .build());
            return true;
        } catch (JsonProcessingException e) {
            log.warn("Serialisierung fehlgeschlagen: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Optional<AsyncIndexingService.IndexTask> takeBlocking(int timeoutSeconds) throws InterruptedException {
        String json = redis.opsForList().rightPop(QUEUE_KEY, Duration.ofSeconds(timeoutSeconds));
        if (json == null) return Optional.empty();
        try {
            TaskDto dto = objectMapper.readValue(json, TaskDto.class);
            return Optional.of(new AsyncIndexingService.IndexTask(dto.attachmentUrl(), dto.attachmentUuid()));
        } catch (JsonProcessingException e) {
            log.warn("Deserialisierung fehlgeschlagen: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void setStatus(String attachmentUuid, IndexJobStatus status) {
        String key = STATUS_KEY_PREFIX + attachmentUuid;
        try {
            String json = objectMapper.writeValueAsString(status);
            redis.opsForValue().set(key, json, Duration.ofHours(statusTtlHours));
        } catch (JsonProcessingException e) {
            log.warn("Status-Serialisierung fehlgeschlagen: {}", e.getMessage());
        }
    }

    @Override
    public Optional<IndexJobStatus> getStatus(String attachmentUuid) {
        String key = STATUS_KEY_PREFIX + attachmentUuid;
        String json = redis.opsForValue().get(key);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, IndexJobStatus.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    @Override
    public long getQueueSize() {
        Long size = redis.opsForList().size(QUEUE_KEY);
        return size != null ? size : 0;
    }

    private record TaskDto(String attachmentUrl, String attachmentUuid) {}
}
