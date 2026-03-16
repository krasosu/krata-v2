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
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integrationstest mit echtem MinIO (Testcontainers): Bucket anlegen, Datei hochladen,
 * indizieren, suchen und Treffer prüfen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@EnabledIf(value = "dockerAvailable", disabledReason = "Docker nicht verfügbar – z. B. in CI mit Docker ausführen")
class IntegrationTest {

    /**
     * Nur bei verfügbarem Docker ausführen. Ohne Docker erscheint ggf. eine ERROR-Logzeile
     * von Testcontainers – der Test wird dann trotzdem korrekt übersprungen (Skipped).
     */
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
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).withStartupTimeout(java.time.Duration.ofSeconds(60)));

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
    void indexFromMinioAndSearch() {
        /* ARRANGE */
        String minioUrl = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000) + "/" + BUCKET + "/" + OBJECT_KEY;
        var indexRequest = new IndexRequest(minioUrl, ATTACHMENT_UUID);
        var searchRequest = new SearchRequest("content:integration", 0, 20, false);

        /* ACT */
        var indexResponse = restTemplate.postForEntity("/api/attachments/index", indexRequest, IndexResponse.class);

        /* ASSERT – Indexierung */
        assertThat(indexResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(indexResponse.getBody()).isNotNull();
        assertThat(indexResponse.getBody().indexed()).isTrue();

        /* ACT – Suche (mit Polling bis Lucene-Commit durch ist, max. 15 s in CI) */
        var searchResponse = await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> restTemplate.postForEntity("/api/search", searchRequest, SearchResponse.class),
                        resp -> {
                            if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) return false;
                            var body = resp.getBody();
                            return body.total() >= 1 && body.hits() != null
                                    && body.hits().stream().anyMatch(hit -> ATTACHMENT_UUID.equals(hit.attachmentUuid()));
                        });

        /* ASSERT – Suchergebnis */
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
