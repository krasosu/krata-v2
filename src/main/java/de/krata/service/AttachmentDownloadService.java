package de.krata.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Downloads objects from an S3-compatible storage using an object URL.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentDownloadService {

    @Value("${s3.access-key}")
    private String accessKey;

    @Value("${s3.secret-key}")
    private String secretKey;

    /**
     * Allowed storage endpoint as base URL (scheme + host + optional port).
     * Example: http://minio:9000 or https://s3.example.tld
     */
    @Value("${s3.base-url:}")
    private String s3BaseUrl;

    private volatile MinioClient s3Client;

    /**
     * Downloads the object behind the provided URL and returns it as an InputStream.
     * The caller is responsible for closing the stream.
     *
     * @param attachmentUrl full object URL (path-style)
     * @return object content stream
     */
    public InputStream download(String attachmentUrl) throws IOException, MinioException, InvalidKeyException, NoSuchAlgorithmException {
        var parsed = parseS3Url(attachmentUrl);
        MinioClient client = getS3Client();
        log.debug("Lade Objekt von S3: endpoint={}, bucket={}, object={}", parsed.endpoint(), parsed.bucket(), parsed.objectKey());

        return client.getObject(
                GetObjectArgs.builder()
                        .bucket(parsed.bucket())
                        .object(parsed.objectKey())
                        .build()
        );
    }

    /**
     * Downloads the full object as a byte array (for Tika/indexing).
     */
    public byte[] downloadAsBytes(String attachmentUrl) throws IOException, MinioException, InvalidKeyException, NoSuchAlgorithmException {
        try (InputStream in = download(attachmentUrl)) {
            return in.readAllBytes();
        }
    }

    /**
     * Parses an S3-compatible object URL and extracts endpoint, bucket and object key.
     *
     * Expects path-style: {s3.base-url}/{bucket}/{objectKey...}
     * The attachmentUrl host must exactly match the configured s3.base-url.
     */
    public S3Location parseS3Url(String attachmentUrl) {
        try {
            URI uri = new URI(attachmentUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("Ungültige Attachment-URL: scheme/host fehlt: " + attachmentUrl);
            }
            String endpoint = normalizeEndpoint(uri);
            String expectedEndpoint = normalizeEndpoint(new URI(requireS3BaseUrl()));
            if (!endpoint.equals(expectedEndpoint)) {
                throw new IllegalArgumentException("Ungültige Attachment-URL: Host passt nicht zum konfigurierten s3.base-url");
            }

            String path = uri.getPath();
            if (path == null || path.isEmpty() || path.equals("/")) {
                throw new IllegalArgumentException("Ungültige Attachment-URL: Pfad fehlt. Erwartet: {s3.base-url}/{bucket}/{objectKey}");
            }
            // Split path into segments (without leading slash)
            String withoutLeading = path.startsWith("/") ? path.substring(1) : path;
            List<String> segments = withoutLeading.isEmpty() ? List.of() : List.of(withoutLeading.split("/"));

            if (segments.size() < 2) {
                throw new IllegalArgumentException("Ungültige Attachment-URL: Bucket und Object-Key fehlen.");
            }
            String bucket = segments.get(0);
            String objectKey = String.join("/", segments.subList(1, segments.size()));
            return new S3Location(endpoint, bucket, objectKey);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Ungültige Attachment-URL: " + attachmentUrl, e);
        }
    }

    private MinioClient getS3Client() {
        MinioClient existing = s3Client;
        if (existing != null) return existing;
        synchronized (this) {
            if (s3Client == null) {
                s3Client = MinioClient.builder()
                        .endpoint(requireS3BaseUrl())
                        .credentials(accessKey, secretKey)
                        .build();
            }
            return s3Client;
        }
    }

    private String requireS3BaseUrl() {
        if (s3BaseUrl == null || s3BaseUrl.isBlank()) {
            throw new IllegalStateException("s3.base-url ist nicht konfiguriert. Bitte S3_BASE_URL setzen.");
        }
        return s3BaseUrl.trim();
    }

    private String normalizeEndpoint(URI uri) {
        int port = uri.getPort();
        if (port < 0) {
            if ("https".equalsIgnoreCase(uri.getScheme())) port = 443;
            if ("http".equalsIgnoreCase(uri.getScheme())) port = 80;
        }
        boolean isDefaultPort = ("https".equalsIgnoreCase(uri.getScheme()) && port == 443)
                || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)
                || port < 0;
        if (isDefaultPort) {
            return uri.getScheme() + "://" + uri.getHost();
        }
        return uri.getScheme() + "://" + uri.getHost() + ":" + port;
    }

    public record S3Location(String endpoint, String bucket, String objectKey) {}
}
