package de.krata.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Extracts text from binary data (PDF, DOCX, TXT, etc.) using Apache Tika.
 */
@Service
@Slf4j
public class TextExtractionService {

    private final Tika tika = new Tika();

    /**
     * Extracts readable text from the given bytes (e.g. from a PDF or DOCX file).
     *
     * @param data raw attachment bytes
     * @param hint optional filename/content-type hint for better detection
     * @return extracted text, or empty string on errors
     */
    public String extractText(byte[] data, String hint) {
        if (data == null || data.length == 0) {
            return "";
        }
        try {
            String text = tika.parseToString(new ByteArrayInputStream(data));
            return text != null ? text : "";
        } catch (TikaException | IOException e) {
            log.warn("Text extraction failed (hint={}): {}", hint, e.getMessage());
            return "";
        }
    }

    /**
     * Extracts text without a filename hint.
     */
    public String extractText(byte[] data) {
        return extractText(data, null);
    }

    /**
     * Detects the MIME type of the given data (e.g. "application/pdf", "audio/mpeg").
     *
     * @param data raw bytes
     * @param hint optional filename hint for better detection
     * @return MIME type or null on errors
     */
    public String detectContentType(byte[] data, String hint) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            return tika.detect(new ByteArrayInputStream(data), hint);
        } catch (IOException e) {
            log.warn("Content-type detection failed (hint={}): {}", hint, e.getMessage());
            return null;
        }
    }
}
