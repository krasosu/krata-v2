package de.krata.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Optionaler S3-Client (MinIO SDK kompatibel zu S3-APIs).
 * Wird nur benötigt für Health-Checks und Test-Setups; die Indizierung nutzt die Endpoint-URL aus dem POST.
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

