package de.krata.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Krata API")
                        .description("Volltextsuche: Attachments aus MinIO indizieren (Lucene) und durchsuchen.")
                        .version("0.0.1"));
    }
}
