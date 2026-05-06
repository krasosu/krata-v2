package de.krata.web;

/**
 * Thrown when the indexing queue is full (503 Service Unavailable).
 */
public class QueueFullException extends RuntimeException {
    public QueueFullException(String message) {
        super(message);
    }
}
