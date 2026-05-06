package de.krata.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Optional S3 client (MinIO SDK is compatible with S3 APIs).
 * Only used for health checks and test setups; indexing uses the configured S3 base URL.
 */
@Configuration
@ConditionalOnProperty(name = "s3.url")
public class S3Config {

    @Value("${s3.url}")
    private String url;

    @Value("${s3.access-key}")
    private String accessKey;

    @Value("${s3.secret-key}")
    private String secretKey;

    @Bean
    public MinioClient s3Client() {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }
}

