package de.krata.health;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnBean(MinioClient.class)
public class S3HealthIndicator implements HealthIndicator {

    private final MinioClient s3Client;

    @Override
    public Health health() {
        try {
            s3Client.listBuckets();
            return Health.up().withDetail("message", "S3 Storage erreichbar").build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}

