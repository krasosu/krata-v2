package de.krata.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentDownloadServiceTest {

    @Test
    void parseRelativePath_resolvesBucketAndKeyAgainstBaseUrl() {
        AttachmentDownloadService svc = new AttachmentDownloadService();
        ReflectionTestUtils.setField(svc, "s3BaseUrl", "http://minio:9000");

        var loc = svc.parseS3Url("data.input/2026-05-04/ajd/data.txt");

        assertThat(loc.endpoint()).isEqualTo("http://minio:9000");
        assertThat(loc.bucket()).isEqualTo("data.input");
        assertThat(loc.objectKey()).isEqualTo("2026-05-04/ajd/data.txt");
    }

    @Test
    void parseRelativePath_stripsLeadingSlashAndSkipsEmptySegments() {
        AttachmentDownloadService svc = new AttachmentDownloadService();
        ReflectionTestUtils.setField(svc, "s3BaseUrl", "https://s3.example.com");

        var loc = svc.parseS3Url("//my-bucket//folder//file.txt");

        assertThat(loc.endpoint()).isEqualTo("https://s3.example.com");
        assertThat(loc.bucket()).isEqualTo("my-bucket");
        assertThat(loc.objectKey()).isEqualTo("folder/file.txt");
    }

    @Test
    void parseAbsoluteHttpUrl_whenEndpointMatchesBase() {
        AttachmentDownloadService svc = new AttachmentDownloadService();
        ReflectionTestUtils.setField(svc, "s3BaseUrl", "http://minio:9000");

        var loc = svc.parseS3Url("http://minio:9000/attachments/integration-test/sample.txt");

        assertThat(loc.bucket()).isEqualTo("attachments");
        assertThat(loc.objectKey()).isEqualTo("integration-test/sample.txt");
    }

    @Test
    void parseRelativePath_rejectsSingleSegment() {
        AttachmentDownloadService svc = new AttachmentDownloadService();
        ReflectionTestUtils.setField(svc, "s3BaseUrl", "http://minio:9000");

        assertThatThrownBy(() -> svc.parseS3Url("only-bucket"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected {bucket}/{objectKey}");
    }
}
