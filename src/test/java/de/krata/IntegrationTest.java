package de.krata;

import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integrationstest mit echtem MinIO (Testcontainers): Bucket anlegen, Datei hochladen,
 * indizieren, suchen und Treffer prüfen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@EnabledIf(value = "dockerAvailable", disabledReason = "Docker nicht verfügbar – z. B. in CI mit Docker ausführen")
class IntegrationTest {

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    private static final String BUCKET = "attachments";
    private static final String OBJECT_KEY = "integration-test/sample.txt";
    private static final String ATTACHMENT_UUID = "integration-test";
    private static final String FILE_CONTENT = "Lucene integration test content for MinIO";

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse("quay.io/minio/minio:latest"))
            .withExposedPorts(9000)
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin");

    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry registry) {
        String url = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
        registry.add("minio.url", () -> url);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MinioClient minioClient;

    @BeforeEach
    void setUpMinio() throws Exception {
        try {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
        } catch (Exception e) {
            if (!e.getMessage().contains("already exists")) {
                throw e;
            }
        }
        byte[] content = FILE_CONTENT.getBytes(StandardCharsets.UTF_8);
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(BUCKET)
                        .object(OBJECT_KEY)
                        .stream(new ByteArrayInputStream(content), content.length, -1)
                        .build()
        );
    }

    @Test
    void indexFromMinioAndSearch() throws InterruptedException {
        /* ARRANGE */
        String minioUrl = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000) + "/" + BUCKET + "/" + OBJECT_KEY;
        var indexRequest = new IndexRequest(minioUrl, ATTACHMENT_UUID);
        var searchRequest = new SearchRequest("content:integration", 0, 20, false);

        /* ACT */
        var indexResponse = restTemplate.postForEntity("/api/attachments/index", indexRequest, IndexResponse.class);
        Thread.sleep(2000); // Scheduled-Commit (commit-interval-sec: 1)
        var searchResponse = restTemplate.postForEntity("/api/search", searchRequest, SearchResponse.class);

        /* ASSERT */
        assertThat(indexResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(indexResponse.getBody()).isNotNull();
        assertThat(indexResponse.getBody().indexed()).isTrue();
        assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(searchResponse.getBody()).isNotNull();
        assertThat(searchResponse.getBody().total()).isGreaterThanOrEqualTo(1);
        assertThat(searchResponse.getBody().hits())
                .anyMatch(hit -> ATTACHMENT_UUID.equals(hit.attachmentUuid()));
    }

    private record IndexRequest(String attachmentUrl, String attachmentUuid) {}
    private record IndexResponse(boolean indexed, String skippedReason) {}
    private record SearchRequest(String query, int from, int size, boolean withHighlight) {}
    private record SearchResponse(long total, int from, int size, java.util.List<SearchHit> hits) {}
    private record SearchHit(String attachmentUuid, String snippet) {}
}
