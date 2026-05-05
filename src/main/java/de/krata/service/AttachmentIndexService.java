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
        long t0 = System.nanoTime();
        log.info("Prüfe Attachment für Indizierung: recordUuid={}, attachmentUuid={}, url={}", recordUuid, attachmentUuid, attachmentUrl);

        long tDownload0 = System.nanoTime();
        byte[] data = attachmentDownloadService.downloadAsBytes(attachmentUrl);
        long tDownloadMs = (System.nanoTime() - tDownload0) / 1_000_000;

        String fileName = attachmentDownloadService.parseS3Url(attachmentUrl).objectKey();
        String mimeType = textExtractionService.detectContentType(data, fileName);

        if (!indexableContentTypeService.isIndexable(mimeType)) {
            long totalMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("Attachment übersprungen: recordUuid={}, attachmentUuid={}, mimeType={}, downloadMs={}, totalMs={}, reason={}",
                    recordUuid, attachmentUuid, mimeType, tDownloadMs, totalMs, "content_type_not_indexable");
            return IndexAttachmentResponse.builder()
                    .recordUuid(recordUuid)
                    .attachmentUuid(attachmentUuid)
                    .indexed(false)
                    .skippedReason("content_type_not_indexable")
                    .build();
        }

        long tExtract0 = System.nanoTime();
        String content = textExtractionService.extractText(data, fileName);
        long tExtractMs = (System.nanoTime() - tExtract0) / 1_000_000;
        if (content.isBlank()) {
            log.warn("Kein Text aus Attachment extrahiert: uuid={}", attachmentUuid);
        }

        long tIndex0 = System.nanoTime();
        luceneIndexService.indexDocument(recordUuid, attachmentUuid, content, documentCreatedAt);
        long tIndexMs = (System.nanoTime() - tIndex0) / 1_000_000;

        long totalMs = (System.nanoTime() - t0) / 1_000_000;
        log.info("Attachment indiziert: recordUuid={}, attachmentUuid={}, mimeType={}, downloadMs={}, extractMs={}, indexMs={}, totalMs={}",
                recordUuid, attachmentUuid, mimeType, tDownloadMs, tExtractMs, tIndexMs, totalMs);
        return IndexAttachmentResponse.builder()
                .recordUuid(recordUuid)
                .attachmentUuid(attachmentUuid)
                .indexed(true)
                .build();
    }
}
