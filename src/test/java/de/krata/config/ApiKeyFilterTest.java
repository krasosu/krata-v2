package de.krata.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ApiKeyFilterTest {

    @Test
    @DisplayName("no configured key: filter is a no-op and chain continues")
    void noConfiguredKeyAllowsAnyRequest() throws Exception {
        /* ARRANGE */
        ApiKeyFilter filter = new ApiKeyFilter(Optional.empty());
        FilterChain chain = mock(FilterChain.class);

        /* ACT */
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        /* ASSERT */
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    @DisplayName("blank configured key behaves like 'no key'")
    void blankConfiguredKeyAllowsRequest() throws Exception {
        /* ARRANGE */
        ApiKeyFilter filter = new ApiKeyFilter(Optional.of("   "));
        FilterChain chain = mock(FilterChain.class);

        /* ACT */
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        /* ASSERT */
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    @DisplayName("invalid X-API-Key returns 401 and short-circuits the chain")
    void invalidKeyReturns401() throws Exception {
        /* ARRANGE */
        ApiKeyFilter filter = new ApiKeyFilter(Optional.of("expected"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-API-Key", "wrong");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        /* ACT */
        filter.doFilter(req, res, chain);

        /* ASSERT */
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("matching X-API-Key passes the chain through")
    void validKeyPassesThrough() throws Exception {
        /* ARRANGE */
        ApiKeyFilter filter = new ApiKeyFilter(Optional.of("expected"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-API-Key", "expected");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        /* ACT */
        filter.doFilter(req, res, chain);

        /* ASSERT */
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verify(chain, times(1)).doFilter(any(), any());
    }
}
