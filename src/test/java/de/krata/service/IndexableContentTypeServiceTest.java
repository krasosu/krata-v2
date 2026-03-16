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
        /* ACT */
        boolean mpeg = service.isIndexable("audio/mpeg");
        boolean wav = service.isIndexable("audio/wav");
        /* ASSERT */
        assertThat(mpeg).isFalse();
        assertThat(wav).isFalse();
    }

    @Test
    void videoNotIndexable() {
        /* ACT */
        boolean result = service.isIndexable("video/mp4");
        /* ASSERT */
        assertThat(result).isFalse();
    }

    @Test
    void imageNotIndexable() {
        /* ACT */
        boolean result = service.isIndexable("image/png");
        /* ASSERT */
        assertThat(result).isFalse();
    }

    @Test
    void pdfIndexable() {
        /* ACT */
        boolean result = service.isIndexable("application/pdf");
        /* ASSERT */
        assertThat(result).isTrue();
    }

    @Test
    void textIndexable() {
        /* ACT */
        boolean result = service.isIndexable("text/plain");
        /* ASSERT */
        assertThat(result).isTrue();
    }

    @Test
    void nullOrBlankNotIndexable() {
        /* ACT */
        boolean nullResult = service.isIndexable(null);
        boolean blankResult = service.isIndexable("");
        /* ASSERT */
        assertThat(nullResult).isFalse();
        assertThat(blankResult).isFalse();
    }
}
