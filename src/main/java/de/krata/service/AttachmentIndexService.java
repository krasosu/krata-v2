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
 * Orchestrates: download object → check content type → extract text → index into Lucene.
 * Audio, video and images are not indexed.
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
     * Downloads the object from the given storage reference ({@code {bucket}/{objectKey}} or legacy full URL).
     * If the content type is indexable (e.g. PDF, DOCX), text is extracted and indexed into Lucene.
     * Audio, video and images are skipped.
     *
     * @param documentCreatedAt optional; user-provided creation time, otherwise indexing time is used
     * @return response with indexed=true/false and optional skippedReason
     */
    public IndexAttachmentResponse indexFromUrl(String attachmentUrl, String attachmentUuid, String recordUuid, Instant documentCreatedAt) throws IOException, MinioException, InvalidKeyException, NoSuchAlgorithmException {
        long t0 = System.nanoTime();
        log.info("Checking attachment for indexing: recordUuid={}, attachmentUuid={}, url={}", recordUuid, attachmentUuid, attachmentUrl);

        long tDownload0 = System.nanoTime();
        byte[] data = attachmentDownloadService.downloadAsBytes(attachmentUrl);
        long tDownloadMs = (System.nanoTime() - tDownload0) / 1_000_000;

        String fileName = attachmentDownloadService.parseS3Url(attachmentUrl).objectKey();
        String mimeType = textExtractionService.detectContentType(data, fileName);

        if (!indexableContentTypeService.isIndexable(mimeType)) {
            long totalMs = (System.nanoTime() - t0) / 1_000_000;
            log.info("Attachment skipped: recordUuid={}, attachmentUuid={}, mimeType={}, downloadMs={}, totalMs={}, reason={}",
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
            log.warn("No text extracted from attachment: uuid={}", attachmentUuid);
        }

        long tIndex0 = System.nanoTime();
        luceneIndexService.indexDocument(recordUuid, attachmentUuid, content, documentCreatedAt);
        long tIndexMs = (System.nanoTime() - tIndex0) / 1_000_000;

        long totalMs = (System.nanoTime() - t0) / 1_000_000;
        log.info("Attachment indexed: recordUuid={}, attachmentUuid={}, mimeType={}, downloadMs={}, extractMs={}, indexMs={}, totalMs={}",
                recordUuid, attachmentUuid, mimeType, tDownloadMs, tExtractMs, tIndexMs, totalMs);
        return IndexAttachmentResponse.builder()
                .recordUuid(recordUuid)
                .attachmentUuid(attachmentUuid)
                .indexed(true)
                .build();
    }
}
