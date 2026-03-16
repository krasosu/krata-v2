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
 * Lädt Attachments aus MinIO/S3 anhand einer Attachment-URL.
 * Erwartet URLs der Form: {minio.url}/{bucket}/{objectKey}
 * z.B. http://localhost:9000/attachments/abc-123/document.pdf
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentDownloadService {

    private final MinioClient minioClient;

    @Value("${minio.url}")
    private String minioBaseUrl;

    /**
     * Lädt die Datei von MinIO anhand der angegebenen URL und gibt den Inhalt als InputStream zurück.
     * Der Aufrufer ist für das Schließen des Streams verantwortlich.
     *
     * @param attachmentUrl vollständige URL zum Objekt (z.B. http://localhost:9000/attachments/uuid/file.pdf)
     * @return InputStream des Objektinhalts
     */
    public InputStream download(String attachmentUrl) throws IOException, MinioException, InvalidKeyException, NoSuchAlgorithmException {
        var parsed = parseMinioUrl(attachmentUrl);
        log.debug("Lade Objekt von MinIO: bucket={}, object={}", parsed.bucket(), parsed.objectKey());

        return minioClient.getObject(
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
     * Parst eine MinIO/S3-URL und extrahiert Bucket und Object-Key.
     * Erwartet: {scheme}://{host}:{port}/{bucket}/{keyPart1}/{keyPart2}/...
     */
    public MinioLocation parseMinioUrl(String attachmentUrl) {
        try {
            URI uri = new URI(attachmentUrl);
            String path = uri.getPath();
            if (path == null || path.isEmpty() || path.equals("/")) {
                throw new IllegalArgumentException("Ungültige Attachment-URL: Pfad fehlt. Erwartet: " + minioBaseUrl + "/{bucket}/{objectKey}");
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
                throw new IllegalArgumentException("Ungültige Attachment-URL: Object-Key fehlt. Erwartet: " + minioBaseUrl + "/{bucket}/{objectKey}");
            }
            return new MinioLocation(bucket, objectKey);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Ungültige Attachment-URL: " + attachmentUrl, e);
        }
    }

    public record MinioLocation(String bucket, String objectKey) {}
}
