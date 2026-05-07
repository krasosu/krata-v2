package de.krata.config;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class S3ConfigTest {

    @Test
    void s3ClientBeanIsBuiltFromUrlAndCredentials() {
        /* ARRANGE */
        S3Config config = new S3Config();
        ReflectionTestUtils.setField(config, "url", "http://minio:9000");
        ReflectionTestUtils.setField(config, "accessKey", "user");
        ReflectionTestUtils.setField(config, "secretKey", "secret");

        /* ACT */
        MinioClient client = config.s3Client();

        /* ASSERT */
        assertThat(client).isNotNull();
    }
}
