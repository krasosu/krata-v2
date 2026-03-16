package de.krata.web;

import de.krata.dto.*;
import de.krata.service.AsyncIndexingService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import de.krata.service.AttachmentIndexService;
import de.krata.service.LuceneIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Attachments & Suche", description = "Attachment indizieren (sync/async/batch), Suche, Löschen")
public class AttachmentController {

    private final AttachmentIndexService attachmentIndexService;
    private final LuceneIndexService luceneIndexService;
    private final AsyncIndexingService asyncIndexingService;

    @Operation(summary = "Attachment indizieren (synchron)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "indexed=true oder false (skippedReason)",
                    content = @Content(schema = @Schema(implementation = IndexAttachmentResponse.class))),
            @ApiResponse(responseCode = "202", description = "Bei async=true: angenommen"),
            @ApiResponse(responseCode = "422", description = "MinIO/Indizierungsfehler"),
            @ApiResponse(responseCode = "503", description = "Queue voll (nur bei async)")
    })
    @RateLimiter(name = "index")
    @PostMapping("/attachments/index")
    public ResponseEntity<?> indexAttachment(
            @Valid @RequestBody IndexAttachmentRequest request,
            @RequestParam(defaultValue = "false") boolean async) throws Exception {

        if (async) {
            boolean accepted = asyncIndexingService.submit(request.getAttachmentUrl(), request.getAttachmentUuid());
            if (!accepted) {
                throw new QueueFullException("Indizierungs-Queue ist voll. Bitte später erneut versuchen.");
            }
            return ResponseEntity.accepted().body(IndexJobStatus.builder()
                    .attachmentUuid(request.getAttachmentUuid())
                    .status(IndexJobStatus.Status.PENDING)
                    .build());
        }
        IndexAttachmentResponse response = attachmentIndexService.indexFromUrl(request.getAttachmentUrl(), request.getAttachmentUuid());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Batch-Indizierung (asynchron)")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Annahmen (accepted kann < Anzahl sein bei voller Queue)"),
            @ApiResponse(responseCode = "400", description = "Validierungsfehler")
    })
    @RateLimiter(name = "index")
    @PostMapping("/attachments/index/batch")
    public ResponseEntity<BatchIndexResponse> indexBatch(@Valid @RequestBody BatchIndexRequest request) {
        List<AsyncIndexingService.IndexTask> tasks = request.getAttachments().stream()
                .map(a -> new AsyncIndexingService.IndexTask(a.getAttachmentUrl(), a.getAttachmentUuid()))
                .toList();
        int accepted = asyncIndexingService.submitBatch(tasks);
        List<IndexJobStatus> jobs = request.getAttachments().stream()
                .limit(accepted)
                .map(a -> IndexJobStatus.builder()
                        .attachmentUuid(a.getAttachmentUuid())
                        .status(IndexJobStatus.Status.PENDING)
                        .build())
                .toList();
        return ResponseEntity.accepted().body(BatchIndexResponse.builder()
                .accepted(accepted)
                .jobs(jobs)
                .build());
    }

    @Operation(summary = "Status eines Indizierungs-Jobs")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status (PENDING/INDEXED/SKIPPED/FAILED)"),
            @ApiResponse(responseCode = "404", description = "UUID unbekannt oder Status bereits verworfen")
    })
    @GetMapping("/attachments/index/status/{attachmentUuid}")
    public ResponseEntity<IndexJobStatus> getIndexStatus(@PathVariable String attachmentUuid) {
        return asyncIndexingService.getStatus(attachmentUuid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Dokument aus Index entfernen")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Gelöscht"),
            @ApiResponse(responseCode = "500", description = "Index-Fehler")
    })
    @DeleteMapping("/attachments/{attachmentUuid}")
    public ResponseEntity<Void> deleteFromIndex(@PathVariable String attachmentUuid) throws Exception {
        luceneIndexService.deleteByAttachmentUuid(attachmentUuid);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Volltextsuche (paginierbar, optional mit Snippets)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Treffer mit total, from, size, hits",
                    content = @Content(schema = @Schema(implementation = PaginatedSearchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Ungültige Lucene-Query")
    })
    @RateLimiter(name = "search")
    @PostMapping("/search")
    public ResponseEntity<PaginatedSearchResponse> search(@Valid @RequestBody SearchRequest request) throws Exception {
        PaginatedSearchResponse result = luceneIndexService.search(
                request.getQuery(),
                request.getFrom(),
                request.getSize(),
                request.isWithHighlight()
        );
        return ResponseEntity.ok(result);
    }
}
