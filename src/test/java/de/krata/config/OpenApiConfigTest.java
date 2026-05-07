package de.krata.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void openApiBeanContainsTitleAndVersion() {
        /* ACT */
        OpenAPI api = new OpenApiConfig().openAPI();

        /* ASSERT */
        assertThat(api.getInfo().getTitle()).isEqualTo("Krata API");
        assertThat(api.getInfo().getVersion()).isEqualTo("0.0.1");
    }
}
