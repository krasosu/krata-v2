package de.krata.service;

import de.krata.dto.IndexAttachmentResponse;
import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Orchestriert: Attachment von MinIO laden → Content-Type prüfen → ggf. Text extrahieren und in Lucene indizieren.
 * Audio, Video und Bilder werden nicht indiziert.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentIndexService {

    private final AttachmentDownloadService attachmentDownloadService;
    private final TextExtractionService textExtractionService;
    private final LuceneIndexService luceneIndexService;
    private final IndexableContentTypeService indexableContentTypeService;

    /**
     * Lädt das Attachment von der angegebenen URL. Wenn der Content-Type indizierbar ist (z.B. PDF, DOCX),
     * wird Text extrahiert und in Lucene indiziert. Audio, Video und Bilder werden übersprungen.
     *
     * @return Response mit indexed=true/false und ggf. skippedReason
     */
    /**
     * @param documentCreatedAt optional; Anwender-Erstellungszeit, sonst wird der Indizierungszeitpunkt verwendet
     */
    public IndexAttachmentResponse indexFromUrl(String attachmentUrl, String attachmentUuid, String recordUuid, Instant documentCreatedAt) throws IOException, MinioException, InvalidKeyException, NoSuchAlgorithmException {
        log.info("Prüfe Attachment für Indizierung: recordUuid={}, attachmentUuid={}, url={}", recordUuid, attachmentUuid, attachmentUrl);

        byte[] data = attachmentDownloadService.downloadAsBytes(attachmentUrl);
        String fileName = attachmentDownloadService.parseS3Url(attachmentUrl).objectKey();
        String mimeType = textExtractionService.detectContentType(data, fileName);

        if (!indexableContentTypeService.isIndexable(mimeType)) {
            log.info("Attachment nicht indiziert (Content-Type nicht für Volltextsuche geeignet): uuid={}, mimeType={}", attachmentUuid, mimeType);
            return IndexAttachmentResponse.builder()
                    .recordUuid(recordUuid)
                    .attachmentUuid(attachmentUuid)
                    .indexed(false)
                    .skippedReason("content_type_not_indexable")
                    .build();
        }

        String content = textExtractionService.extractText(data, fileName);
        if (content.isBlank()) {
            log.warn("Kein Text aus Attachment extrahiert: uuid={}", attachmentUuid);
        }

        luceneIndexService.indexDocument(recordUuid, attachmentUuid, content, documentCreatedAt);
        log.info("Attachment indiziert: recordUuid={}, attachmentUuid={}", recordUuid, attachmentUuid);
        return IndexAttachmentResponse.builder()
                .recordUuid(recordUuid)
                .attachmentUuid(attachmentUuid)
                .indexed(true)
                .build();
    }
}
