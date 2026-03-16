package de.krata.service;

import de.krata.dto.IndexJobStatus;

import java.util.Optional;

/**
 * Backend für die Indizierungs-Queue (In-Memory oder Redis).
 * Ermöglicht persistente Queue über Neustarts hinweg (Redis).
 */
public interface IndexingQueueBackend {

    /** Stellt einen Job in die Queue. @return true wenn angenommen */
    boolean submit(AsyncIndexingService.IndexTask task);

    /** Blockierendes Entnehmen des nächsten Jobs (Timeout in Sekunden). @return leer wenn Timeout */
    Optional<AsyncIndexingService.IndexTask> takeBlocking(int timeoutSeconds) throws InterruptedException;

    void setStatus(String attachmentUuid, IndexJobStatus status);

    Optional<IndexJobStatus> getStatus(String attachmentUuid);

    /** Aktuelle Anzahl Jobs in der Queue (Best-Effort). */
    long getQueueSize();
}
