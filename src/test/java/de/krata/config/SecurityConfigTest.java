package de.krata.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void filterRegistrationCreatedWithEmptyKey() {
        /* ARRANGE */
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "apiKey", "");

        /* ACT */
        FilterRegistrationBean<ApiKeyFilter> bean = config.apiKeyFilter();

        /* ASSERT */
        assertThat(bean).isNotNull();
        assertThat(bean.getFilter()).isInstanceOf(ApiKeyFilter.class);
        assertThat(bean.getUrlPatterns()).contains("/api/*");
        assertThat(bean.getOrder()).isEqualTo(1);
    }

    @Test
    void filterRegistrationCreatedWithRealKey() {
        /* ARRANGE */
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "apiKey", "secret");

        /* ACT */
        FilterRegistrationBean<ApiKeyFilter> bean = config.apiKeyFilter();

        /* ASSERT */
        assertThat(bean.getFilter()).isInstanceOf(ApiKeyFilter.class);
    }

    @Test
    void filterRegistrationCreatedWithNullKey() {
        /* ARRANGE */
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "apiKey", null);

        /* ACT */
        FilterRegistrationBean<ApiKeyFilter> bean = config.apiKeyFilter();

        /* ASSERT */
        assertThat(bean.getFilter()).isInstanceOf(ApiKeyFilter.class);
    }
}
