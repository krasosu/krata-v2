package de.krata.web;

/**
 * Wird geworfen, wenn die Indizierungs-Queue voll ist (503 Service Unavailable).
 */
public class QueueFullException extends RuntimeException {
    public QueueFullException(String message) {
        super(message);
    }
}
