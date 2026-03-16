package de.krata.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndexableContentTypeServiceTest {

    private IndexableContentTypeService service;

    @BeforeEach
    void setUp() {
        service = new IndexableContentTypeService();
    }

    @Test
    void audioNotIndexable() {
        assertThat(service.isIndexable("audio/mpeg")).isFalse();
        assertThat(service.isIndexable("audio/wav")).isFalse();
    }

    @Test
    void videoNotIndexable() {
        assertThat(service.isIndexable("video/mp4")).isFalse();
    }

    @Test
    void imageNotIndexable() {
        assertThat(service.isIndexable("image/png")).isFalse();
    }

    @Test
    void pdfIndexable() {
        assertThat(service.isIndexable("application/pdf")).isTrue();
    }

    @Test
    void textIndexable() {
        assertThat(service.isIndexable("text/plain")).isTrue();
    }

    @Test
    void nullOrBlankNotIndexable() {
        assertThat(service.isIndexable(null)).isFalse();
        assertThat(service.isIndexable("")).isFalse();
    }
}
