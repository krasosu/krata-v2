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
import java.util.Arrays;
import java.util.List;

/**
 * Downloads objects from an S3-compatible storage using an object URL or a path relative to {@code s3.base-url}.
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
     * Storage endpoint (scheme + host + optional port). Used with relative object paths and for the MinIO client.
     * Example: http://minio:9000 or https://s3.example.tld
     */
    @Value("${s3.base-url:}")
    private String s3BaseUrl;

    private volatile MinioClient s3Client;

    /**
     * Downloads the object behind the provided reference and returns it as an InputStream.
     * The caller is responsible for closing the stream.
     *
     * @param attachmentUrl either a path-style object reference {@code {bucket}/{objectKey}} (no scheme), resolved
     *                       against {@code s3.base-url}, or for backward compatibility a full URL
     *                       {@code {s3.base-url}/{bucket}/{objectKey}}
     * @return object content stream
     */
    public InputStream download(String attachmentUrl) throws IOException, MinioException, InvalidKeyException, NoSuchAlgorithmException {
        var parsed = parseS3Url(attachmentUrl);
        MinioClient client = getS3Client();
        log.debug("Loading object from S3: endpoint={}, bucket={}, object={}", parsed.endpoint(), parsed.bucket(), parsed.objectKey());

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
     * Resolves bucket and object key from the client-supplied value.
     * <ul>
     *   <li><strong>Relative (preferred):</strong> {@code bucket/object/key...} — must be configured with
     *       {@code s3.base-url} / {@code S3_BASE_URL}.</li>
     *   <li><strong>Absolute (legacy):</strong> {@code http(s)://{host}/{bucket}/{objectKey...}} where the endpoint
     *       matches {@code s3.base-url}.</li>
     * </ul>
     */
    public S3Location parseS3Url(String attachmentUrl) {
        if (attachmentUrl == null || attachmentUrl.isBlank()) {
            throw new IllegalArgumentException("attachmentUrl must not be blank");
        }
        String trimmed = attachmentUrl.trim();
        if (looksLikeAbsoluteHttpUrl(trimmed)) {
            return parseAbsoluteHttpUrl(trimmed);
        }
        return parseRelativeObjectPath(trimmed);
    }

    private boolean looksLikeAbsoluteHttpUrl(String s) {
        try {
            URI uri = new URI(s);
            String scheme = uri.getScheme();
            return scheme != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private S3Location parseAbsoluteHttpUrl(String attachmentUrl) {
        try {
            URI uri = new URI(attachmentUrl);
            String endpoint = normalizeEndpoint(uri);
            String expectedEndpoint = normalizeEndpoint(new URI(requireS3BaseUrl()));
            if (!endpoint.equals(expectedEndpoint)) {
                throw new IllegalArgumentException("Attachment URL host does not match configured s3.base-url");
            }

            String path = uri.getPath();
            if (path == null || path.isEmpty() || path.equals("/")) {
                throw new IllegalArgumentException("Attachment URL path is missing; expected {s3.base-url}/{bucket}/{objectKey}");
            }
            String withoutLeading = path.startsWith("/") ? path.substring(1) : path;
            List<String> segments = pathSegments(withoutLeading);

            if (segments.size() < 2) {
                throw new IllegalArgumentException("Attachment URL must include bucket and object key");
            }
            String bucket = segments.get(0);
            String objectKey = String.join("/", segments.subList(1, segments.size()));
            return new S3Location(endpoint, bucket, objectKey);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid attachment URL: " + attachmentUrl, e);
        }
    }

    private S3Location parseRelativeObjectPath(String path) {
        try {
            String endpoint = normalizeEndpoint(new URI(requireS3BaseUrl()));
            String withoutLeading = path.startsWith("/") ? path.substring(1) : path;
            List<String> segments = pathSegments(withoutLeading);
            if (segments.size() < 2) {
                throw new IllegalArgumentException("Invalid attachment path: expected {bucket}/{objectKey}, got: " + path);
            }
            String bucket = segments.get(0);
            String objectKey = String.join("/", segments.subList(1, segments.size()));
            return new S3Location(endpoint, bucket, objectKey);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid s3.base-url configuration", e);
        }
    }

    private static List<String> pathSegments(String pathWithoutLeadingSlash) {
        return Arrays.stream(pathWithoutLeadingSlash.split("/"))
                .filter(s -> !s.isEmpty())
                .toList();
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
            throw new IllegalStateException("s3.base-url is not configured; set S3_BASE_URL");
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
