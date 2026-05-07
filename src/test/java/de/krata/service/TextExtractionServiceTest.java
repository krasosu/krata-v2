package de.krata.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TextExtractionServiceTest {

    private final TextExtractionService service = new TextExtractionService();

    @Test
    void extractTextReturnsContentForPlainBytes() {
        /* ARRANGE */
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);

        /* ACT */
        String text = service.extractText(data, "sample.txt");

        /* ASSERT */
        assertThat(text).contains("hello world");
    }

    @Test
    void extractTextWithoutHintWorksToo() {
        /* ARRANGE */
        byte[] data = "plain".getBytes(StandardCharsets.UTF_8);

        /* ACT */
        String text = service.extractText(data);

        /* ASSERT */
        assertThat(text).contains("plain");
    }

    @Test
    void extractTextReturnsEmptyStringForNullOrEmptyBytes() {
        /* ACT & ASSERT */
        assertThat(service.extractText(null, "x")).isEmpty();
        assertThat(service.extractText(new byte[0], "x")).isEmpty();
    }

    @Test
    void detectContentTypeReturnsTextPlainForAsciiBytes() {
        /* ARRANGE */
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);

        /* ACT */
        String mime = service.detectContentType(data, "hello.txt");

        /* ASSERT */
        assertThat(mime).startsWith("text/");
    }

    @Test
    void detectContentTypeReturnsNullForNullOrEmptyBytes() {
        /* ACT & ASSERT */
        assertThat(service.detectContentType(null, "x")).isNull();
        assertThat(service.detectContentType(new byte[0], "x")).isNull();
    }
}
