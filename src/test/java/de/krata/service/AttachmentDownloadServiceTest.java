package de.krata.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentDownloadServiceTest {

    private AttachmentDownloadService serviceWithBaseUrl(String baseUrl) {
        AttachmentDownloadService svc = new AttachmentDownloadService();
        ReflectionTestUtils.setField(svc, "s3BaseUrl", baseUrl);
        return svc;
    }

    @Test
    @DisplayName("parseS3Url: relative {bucket}/{key} is resolved against s3.base-url")
    void parseRelativePath_resolvesBucketAndKeyAgainstBaseUrl() {
        /* ARRANGE */
        AttachmentDownloadService svc = serviceWithBaseUrl("http://minio:9000");

        /* ACT */
        var loc = svc.parseS3Url("data.input/2026-05-04/ajd/data.txt");

        /* ASSERT */
        assertThat(loc.endpoint()).isEqualTo("http://minio:9000");
        assertThat(loc.bucket()).isEqualTo("data.input");
        assertThat(loc.objectKey()).isEqualTo("2026-05-04/ajd/data.txt");
    }

    @Test
    @DisplayName("parseS3Url: leading slashes and empty segments are tolerated")
    void parseRelativePath_stripsLeadingSlashAndSkipsEmptySegments() {
        /* ARRANGE */
        AttachmentDownloadService svc = serviceWithBaseUrl("https://s3.example.com");

        /* ACT */
        var loc = svc.parseS3Url("//my-bucket//folder//file.txt");

        /* ASSERT */
        assertThat(loc.endpoint()).isEqualTo("https://s3.example.com");
        assertThat(loc.bucket()).isEqualTo("my-bucket");
        assertThat(loc.objectKey()).isEqualTo("folder/file.txt");
    }

    @Test
    @DisplayName("parseS3Url: legacy absolute URL with matching endpoint still works")
    void parseAbsoluteHttpUrl_whenEndpointMatchesBase() {
        /* ARRANGE */
        AttachmentDownloadService svc = serviceWithBaseUrl("http://minio:9000");

        /* ACT */
        var loc = svc.parseS3Url("http://minio:9000/attachments/integration-test/sample.txt");

        /* ASSERT */
        assertThat(loc.bucket()).isEqualTo("attachments");
        assertThat(loc.objectKey()).isEqualTo("integration-test/sample.txt");
    }

    @Test
    void parseRelativePath_rejectsSingleSegment() {
        /* ARRANGE */
        AttachmentDownloadService svc = serviceWithBaseUrl("http://minio:9000");

        /* ACT & ASSERT */
        assertThatThrownBy(() -> svc.parseS3Url("only-bucket"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected {bucket}/{objectKey}");
    }

    @Test
    void parseS3Url_rejectsBlankInput() {
        /* ARRANGE */
        AttachmentDownloadService svc = serviceWithBaseUrl("http://minio:9000");

        /* ACT & ASSERT */
        assertThatThrownBy(() -> svc.parseS3Url(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
        assertThatThrownBy(() -> svc.parseS3Url(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("parseS3Url: absolute URL with foreign host is rejected")
    void parseAbsoluteHttpUrl_rejectsHostMismatch() {
        /* ARRANGE */
        AttachmentDownloadService svc = serviceWithBaseUrl("http://minio:9000");

        /* ACT & ASSERT */
        assertThatThrownBy(() -> svc.parseS3Url("http://otherhost:9000/bucket/file"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host does not match");
    }

    @Test
    void parseAbsoluteHttpUrl_rejectsMissingPath() {
        /* ARRANGE */
        AttachmentDownloadService svc = serviceWithBaseUrl("http://minio:9000");

        /* ACT & ASSERT */
        assertThatThrownBy(() -> svc.parseS3Url("http://minio:9000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path is missing");
    }

    @Test
    void parseAbsoluteHttpUrl_rejectsSingleSegment() {
        /* ARRANGE */
        AttachmentDownloadService svc = serviceWithBaseUrl("http://minio:9000");

        /* ACT & ASSERT */
        assertThatThrownBy(() -> svc.parseS3Url("http://minio:9000/only-bucket"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket and object key");
    }

    @Test
    @DisplayName("parseS3Url: missing s3.base-url surfaces an IllegalStateException")
    void parseS3Url_rejectsWhenBaseUrlNotConfigured() {
        /* ARRANGE */
        AttachmentDownloadService svc = serviceWithBaseUrl("");

        /* ACT & ASSERT */
        assertThatThrownBy(() -> svc.parseS3Url("bucket/file.txt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("s3.base-url is not configured");
    }

    @Test
    void parseAbsoluteHttpsUrl_normalizesDefaultPort() {
        /* ARRANGE */
        AttachmentDownloadService svc = serviceWithBaseUrl("https://s3.example.com");

        /* ACT */
        var loc = svc.parseS3Url("https://s3.example.com:443/bucket/key");

        /* ASSERT */
        assertThat(loc.bucket()).isEqualTo("bucket");
        assertThat(loc.objectKey()).isEqualTo("key");
    }

    @Test
    void parseAbsoluteHttpUrlWithCustomPortIsKept() {
        /* ARRANGE */
        AttachmentDownloadService svc = serviceWithBaseUrl("http://minio:9001");

        /* ACT */
        var loc = svc.parseS3Url("http://minio:9001/bucket/key");

        /* ASSERT */
        assertThat(loc.endpoint()).isEqualTo("http://minio:9001");
    }
}
