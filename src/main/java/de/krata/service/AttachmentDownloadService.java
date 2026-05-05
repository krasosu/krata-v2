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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lädt Attachments aus einem S3-kompatiblen Storage anhand einer Objekt-URL.
 * Erwartet URLs der Form: {scheme}://{host}:{port}/{bucket}/{objectKey}
 * z.B. http://localhost:9000/attachments/abc-123/document.pdf.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentDownloadService {

    private final ConcurrentHashMap<String, MinioClient> clientByEndpoint = new ConcurrentHashMap<>();

    @Value("${s3.access-key}")
    private String accessKey;

    @Value("${s3.secret-key}")
    private String secretKey;

    /**
     * Lädt die Datei aus einem S3-kompatiblen Storage anhand der angegebenen URL und gibt den Inhalt als InputStream zurück.
     * Der Aufrufer ist für das Schließen des Streams verantwortlich.
     *
     * @param attachmentUrl vollständige URL zum Objekt (z.B. http://localhost:9000/attachments/uuid/file.pdf)
     * @return InputStream des Objektinhalts
     */
    public InputStream download(String attachmentUrl) throws IOException, MinioException, InvalidKeyException, NoSuchAlgorithmException {
        var parsed = parseS3Url(attachmentUrl);
        MinioClient client = getClientForEndpoint(parsed.endpoint());
        log.debug("Lade Objekt von S3: endpoint={}, bucket={}, object={}", parsed.endpoint(), parsed.bucket(), parsed.objectKey());

        return client.getObject(
                GetObjectArgs.builder()
                        .bucket(parsed.bucket())
                        .object(parsed.objectKey())
                        .build()
        );
    }

    /**
     * Lädt die komplette Datei als Byte-Array (für Tika/Indizierung).
     */
    public byte[] downloadAsBytes(String attachmentUrl) throws IOException, MinioException, InvalidKeyException, NoSuchAlgorithmException {
        try (InputStream in = download(attachmentUrl)) {
            return in.readAllBytes();
        }
    }

    /**
     * Parst eine S3-kompatible Objekt-URL und extrahiert Endpoint, Bucket und Object-Key.
     * Erwartet: {scheme}://{host}:{port}/{bucket}/{keyPart1}/{keyPart2}/...
     */
    public S3Location parseS3Url(String attachmentUrl) {
        try {
            URI uri = new URI(attachmentUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("Ungültige Attachment-URL: scheme/host fehlt: " + attachmentUrl);
            }
            String endpoint = uri.getPort() > 0
                    ? uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort()
                    : uri.getScheme() + "://" + uri.getHost();
            String path = uri.getPath();
            if (path == null || path.isEmpty() || path.equals("/")) {
                throw new IllegalArgumentException("Ungültige Attachment-URL: Pfad fehlt. Erwartet: {endpoint}/{bucket}/{objectKey}");
            }
            // Pfad ohne führenden Slash in Segmente zerlegen
            String withoutLeading = path.startsWith("/") ? path.substring(1) : path;
            List<String> segments = List.of(withoutLeading.split("/"));
            if (segments.isEmpty()) {
                throw new IllegalArgumentException("Ungültige Attachment-URL: Bucket und Object-Key fehlen.");
            }
            String bucket = segments.get(0);
            String objectKey = segments.size() > 1
                    ? String.join("/", segments.subList(1, segments.size()))
                    : "";
            if (objectKey.isEmpty()) {
                throw new IllegalArgumentException("Ungültige Attachment-URL: Object-Key fehlt. Erwartet: {endpoint}/{bucket}/{objectKey}");
            }
            return new S3Location(endpoint, bucket, objectKey);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Ungültige Attachment-URL: " + attachmentUrl, e);
        }
    }

    private MinioClient getClientForEndpoint(String endpoint) {
        return clientByEndpoint.computeIfAbsent(endpoint, ep ->
                MinioClient.builder()
                        .endpoint(ep)
                        .credentials(accessKey, secretKey)
                        .build()
        );
    }

    public record S3Location(String endpoint, String bucket, String objectKey) {}
}
