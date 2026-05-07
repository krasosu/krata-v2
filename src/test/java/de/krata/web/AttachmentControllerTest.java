package de.krata.web;

import de.krata.dto.*;
import de.krata.service.AsyncIndexingService;
import de.krata.service.AttachmentIndexService;
import de.krata.service.LuceneIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AttachmentController.class)
class AttachmentControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AttachmentIndexService attachmentIndexService;

    @MockBean
    private LuceneIndexService luceneIndexService;

    @MockBean
    private AsyncIndexingService asyncIndexingService;

    @Test
    void searchReturnsPaginatedResult() throws Exception {
        /* ARRANGE */
        var response = PaginatedSearchResponse.builder()
                .total(1)
                .from(0)
                .size(20)
                .hits(List.of(SearchResultHit.builder().recordUuid("r1").attachmentUuid("u1").build()))
                .build();
        when(luceneIndexService.search(eq("content:test"), eq(0), eq(20), eq(false), isNull(), isNull())).thenReturn(response);
        /* ACT */
        var result = mvc.perform(post("/api/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"content:test\",\"from\":0,\"size\":20}"));
        /* ASSERT */
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.hits[0].recordUuid").value("r1"))
                .andExpect(jsonPath("$.hits[0].attachmentUuid").value("u1"));
    }

    @Test
    void deleteFromIndexReturns204() throws Exception {
        /* ACT */
        var result = mvc.perform(delete("/api/attachments/uuid-123"));
        /* ASSERT */
        result.andExpect(status().isNoContent());
    }

    @Test
    void indexSyncReturns200() throws Exception {
        /* ARRANGE */
        when(attachmentIndexService.indexFromUrl(anyString(), anyString(), anyString(), any()))
                .thenReturn(IndexAttachmentResponse.builder().recordUuid("r1").attachmentUuid("u1").indexed(true).build());
        /* ACT */
        var result = mvc.perform(post("/api/attachments/index")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentUrl\":\"attachments/x/file.pdf\",\"attachmentUuid\":\"u1\",\"recordUuid\":\"r1\"}"));
        /* ASSERT */
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.recordUuid").value("r1"))
                .andExpect(jsonPath("$.attachmentUuid").value("u1"))
                .andExpect(jsonPath("$.indexed").value(true));
    }

    @Test
    void indexAsyncReturns202() throws Exception {
        /* ARRANGE */
        when(asyncIndexingService.submit(anyString(), anyString(), anyString(), any())).thenReturn(true);
        /* ACT */
        var result = mvc.perform(post("/api/attachments/index?async=true")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentUrl\":\"attachments/x/file.pdf\",\"attachmentUuid\":\"u1\",\"recordUuid\":\"r1\"}"));
        /* ASSERT */
        result.andExpect(status().isAccepted());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("async index: 503 with QUEUE_FULL when the queue rejects a task")
    void indexAsyncReturns503WhenQueueFull() throws Exception {
        /* ARRANGE */
        when(asyncIndexingService.submit(anyString(), anyString(), anyString(), any())).thenReturn(false);

        /* ACT */
        var result = mvc.perform(post("/api/attachments/index?async=true")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentUrl\":\"attachments/x/file.pdf\",\"attachmentUuid\":\"u1\",\"recordUuid\":\"r1\"}"));

        /* ASSERT */
        result.andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("QUEUE_FULL"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("batch index: 202 with one PENDING job per accepted attachment")
    void indexBatchReturnsAcceptedWithJobs() throws Exception {
        /* ARRANGE */
        when(asyncIndexingService.submitBatch(any())).thenReturn(2);
        String body = "{\"attachments\":[" +
                "{\"attachmentUrl\":\"b/k1\",\"attachmentUuid\":\"u1\",\"recordUuid\":\"r1\"}," +
                "{\"attachmentUrl\":\"b/k2\",\"attachmentUuid\":\"u2\",\"recordUuid\":\"r2\"}" +
                "]}";

        /* ACT */
        var result = mvc.perform(post("/api/attachments/index/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        /* ASSERT */
        result.andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(2))
                .andExpect(jsonPath("$.jobs", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void getIndexStatusReturns200WhenKnown() throws Exception {
        /* ARRANGE */
        when(asyncIndexingService.getStatus("u1")).thenReturn(java.util.Optional.of(
                IndexJobStatus.builder()
                        .attachmentUuid("u1")
                        .recordUuid("r1")
                        .status(IndexJobStatus.Status.INDEXED)
                        .indexed(true)
                        .build()));

        /* ACT */
        var result = mvc.perform(get("/api/attachments/index/status/u1"));

        /* ASSERT */
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INDEXED"));
    }

    @Test
    void getIndexStatusReturns404WhenUnknown() throws Exception {
        /* ARRANGE */
        when(asyncIndexingService.getStatus("missing")).thenReturn(java.util.Optional.empty());

        /* ACT */
        var result = mvc.perform(get("/api/attachments/index/status/missing"));

        /* ASSERT */
        result.andExpect(status().isNotFound());
    }

    @Test
    void bulkDeleteReturns204() throws Exception {
        /* ACT */
        var result = mvc.perform(delete("/api/attachments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentUuids\":[\"u1\",\"u2\"]}"));

        /* ASSERT */
        result.andExpect(status().isNoContent());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("bulk delete: 400 when attachmentUuids list is empty (validation)")
    void bulkDeleteReturns400WhenListEmpty() throws Exception {
        /* ACT */
        var result = mvc.perform(delete("/api/attachments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentUuids\":[]}"));

        /* ASSERT */
        result.andExpect(status().isBadRequest());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("search: 400 with VALIDATION_ERROR when query is blank")
    void searchValidationFailsForBlankQuery() throws Exception {
        /* ACT */
        var result = mvc.perform(post("/api/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"\",\"from\":0,\"size\":20}"));

        /* ASSERT */
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
