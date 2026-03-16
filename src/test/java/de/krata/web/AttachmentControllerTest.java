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
                .hits(List.of(SearchResultHit.builder().attachmentUuid("u1").build()))
                .build();
        when(luceneIndexService.search(eq("content:test"), eq(0), eq(20), eq(false))).thenReturn(response);
        /* ACT */
        var result = mvc.perform(post("/api/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"content:test\",\"from\":0,\"size\":20}"));
        /* ASSERT */
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
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
        when(attachmentIndexService.indexFromUrl(anyString(), anyString()))
                .thenReturn(IndexAttachmentResponse.builder().indexed(true).build());
        /* ACT */
        var result = mvc.perform(post("/api/attachments/index")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentUrl\":\"http://minio/attachments/x/file.pdf\",\"attachmentUuid\":\"u1\"}"));
        /* ASSERT */
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.indexed").value(true));
    }

    @Test
    void indexAsyncReturns202() throws Exception {
        /* ARRANGE */
        when(asyncIndexingService.submit(anyString(), anyString())).thenReturn(true);
        /* ACT */
        var result = mvc.perform(post("/api/attachments/index?async=true")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attachmentUrl\":\"http://minio/attachments/x/file.pdf\",\"attachmentUuid\":\"u1\"}"));
        /* ASSERT */
        result.andExpect(status().isAccepted());
    }
}
