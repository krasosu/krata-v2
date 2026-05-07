package de.krata.health;

import io.minio.MinioClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class S3HealthIndicatorTest {

    @Test
    @DisplayName("UP when MinIO listBuckets succeeds")
    void reportsUpWhenListBucketsSucceeds() {
        /* ARRANGE */
        MinioClient client = mock(MinioClient.class);

        /* ACT */
        Health health = new S3HealthIndicator(client).health();

        /* ASSERT */
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("DOWN when MinIO listBuckets throws (storage unreachable)")
    void reportsDownWhenListBucketsThrows() throws Exception {
        /* ARRANGE */
        MinioClient client = mock(MinioClient.class);
        doThrow(new RuntimeException("kaputt")).when(client).listBuckets();

        /* ACT */
        Health health = new S3HealthIndicator(client).health();

        /* ASSERT */
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error", "kaputt");
    }
}
