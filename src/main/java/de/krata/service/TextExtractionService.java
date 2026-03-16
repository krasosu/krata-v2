package de.krata.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Extrahiert Text aus Binärdaten (PDF, DOCX, TXT, etc.) mit Apache Tika.
 */
@Service
@Slf4j
public class TextExtractionService {

    private final Tika tika = new Tika();

    /**
     * Extrahiert lesbaren Text aus den angegebenen Bytes (z.B. aus einer PDF- oder DOCX-Datei).
     *
     * @param data   Rohdaten des Attachments
     * @param hint   optionaler Dateiname/Content-Type-Hinweis für bessere Erkennung
     * @return extrahierter Text oder leere Zeichenkette bei Fehlern
     */
    public String extractText(byte[] data, String hint) {
        if (data == null || data.length == 0) {
            return "";
        }
        try {
            String text = tika.parseToString(new ByteArrayInputStream(data));
            return text != null ? text : "";
        } catch (TikaException | IOException e) {
            log.warn("Textextraktion fehlgeschlagen (hint={}): {}", hint, e.getMessage());
            return "";
        }
    }

    /**
     * Extrahiert Text ohne Dateinamen-Hinweis.
     */
    public String extractText(byte[] data) {
        return extractText(data, null);
    }

    /**
     * Erkennt den MIME-Type der Daten (z.B. "application/pdf", "audio/mpeg").
     *
     * @param data  Rohdaten
     * @param hint  optionaler Dateiname für bessere Erkennung
     * @return MIME-Type oder null bei Fehler
     */
    public String detectContentType(byte[] data, String hint) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            return tika.detect(new ByteArrayInputStream(data), hint);
        } catch (IOException e) {
            log.warn("Content-Type-Erkennung fehlgeschlagen (hint={}): {}", hint, e.getMessage());
            return null;
        }
    }
}
