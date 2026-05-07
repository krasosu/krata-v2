package de.krata.web;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.minio.errors.MinioException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.apache.lucene.queryparser.classic.ParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest req(String path) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn(path);
        return req;
    }

    @Test
    @DisplayName("MethodArgumentNotValidException → 400 with field errors")
    void handleValidationReturns400WithFieldErrors() {
        /* ARRANGE */
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        when(br.getFieldErrors()).thenReturn(List.of(new FieldError("obj", "f", "must not be blank")));
        when(ex.getBindingResult()).thenReturn(br);

        /* ACT */
        ResponseEntity<ApiError> response = handler.handleValidation(ex, req("/api/x"));

        /* ASSERT */
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getFields()).hasSize(1);
    }

    @Test
    @DisplayName("MethodArgumentNotValidException with null defaultMessage falls back to 'invalid'")
    void handleValidationFallsBackWhenDefaultMessageNull() {
        /* ARRANGE */
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        when(br.getFieldErrors()).thenReturn(List.of(new FieldError("obj", "f", null)));
        when(ex.getBindingResult()).thenReturn(br);

        /* ACT */
        ResponseEntity<ApiError> response = handler.handleValidation(ex, req("/x"));

        /* ASSERT */
        assertThat(response.getBody().getFields()).singleElement()
                .satisfies(fe -> assertThat(fe.getMessage()).isEqualTo("invalid"));
    }

    @Test
    void handleConstraintReturns400() {
        /* ACT */
        ResponseEntity<ApiError> response = handler.handleConstraint(
                new ConstraintViolationException("nope", null), req("/api/x"));

        /* ASSERT */
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void handleIOExceptionReturns422() {
        /* ACT */
        ResponseEntity<ApiError> response = handler.handleIOException(new IOException("io"), req("/x"));

        /* ASSERT */
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getCode()).isEqualTo("INDEX_ERROR");
    }

    @Test
    @DisplayName("Minio/InvalidKey/NoSuchAlgorithm exceptions all map to 422 STORAGE_ERROR")
    void handleS3CoversMinioInvalidKeyAndAlgo() {
        /* ACT */
        ResponseEntity<ApiError> a = handler.handleS3(new MinioException("m"), req("/x"));
        ResponseEntity<ApiError> b = handler.handleS3(new InvalidKeyException("k"), req("/x"));
        ResponseEntity<ApiError> c = handler.handleS3(new NoSuchAlgorithmException("a"), req("/x"));

        /* ASSERT */
        assertThat(a.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(b.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(c.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void handleParseExceptionReturns400WithInvalidQueryCode() {
        /* ACT */
        ResponseEntity<ApiError> response = handler.handleParseException(new ParseException("bad"), req("/x"));

        /* ASSERT */
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_QUERY");
    }

    @Test
    void handleRateLimitReturns429() {
        /* ARRANGE */
        RequestNotPermitted ex = mock(RequestNotPermitted.class);

        /* ACT */
        ResponseEntity<ApiError> response = handler.handleRateLimit(ex, req("/x"));

        /* ASSERT */
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().getCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void handleQueueFullReturns503() {
        /* ACT */
        ResponseEntity<ApiError> response = handler.handleQueueFull(
                new QueueFullException("queue is full"), req("/x"));

        /* ASSERT */
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getCode()).isEqualTo("QUEUE_FULL");
    }

    @Test
    void handleIllegalArgReturns400() {
        /* ACT */
        ResponseEntity<ApiError> response = handler.handleIllegalArg(
                new IllegalArgumentException("boom"), req("/x"));

        /* ASSERT */
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void handleGenericReturns500() {
        /* ACT */
        ResponseEntity<ApiError> response = handler.handleGeneric(new RuntimeException("x"), req("/x"));

        /* ASSERT */
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
    }
}
