package de.krata.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndexableContentTypeServiceExtraTest {

    private final IndexableContentTypeService service = new IndexableContentTypeService();

    @Test
    void contentTypeWithCharsetParameterIsHandled() {
        /* ACT & ASSERT */
        assertThat(service.isIndexable("text/plain; charset=utf-8")).isTrue();
        assertThat(service.isIndexable("AUDIO/MPEG; foo=bar")).isFalse();
    }

    @Test
    void blankAndNullAreNotIndexable() {
        /* ACT & ASSERT */
        assertThat(service.isIndexable(null)).isFalse();
        assertThat(service.isIndexable("")).isFalse();
        assertThat(service.isIndexable("   ")).isFalse();
    }
}
