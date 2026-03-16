package de.krata.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class SecurityConfig {

    @Value("${krata.api-key:}")
    private String apiKey;

    @Bean
    public FilterRegistrationBean<ApiKeyFilter> apiKeyFilter() {
        FilterRegistrationBean<ApiKeyFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new ApiKeyFilter(Optional.ofNullable(apiKey).filter(s -> !s.isBlank())));
        reg.addUrlPatterns("/api/*");
        reg.setOrder(1);
        return reg;
    }
}
