# Krata – Volltextsuche mit Lucene und MinIO

Spring-Boot-Anwendung (JDK 21, Lombok) zur Volltext-Indizierung von Attachments aus einem S3-kompatiblen Storage (MinIO) mit Apache Lucene. Ausgelegt auf **hohen Durchsatz** (z.B. 50.000 Attachments/Tag) und **stabilen Produktivbetrieb** mit Retention (14/30 Tage), asynchroner Indizierung, Rate-Limiting und Health-Checks.

## Ablauf

1. **Indizierung:** Über REST wird eine Attachment-URL (MinIO) und eine `attachment_uuid` übergeben. Die Anwendung lädt die Datei aus MinIO, prüft den Content-Type und indiziert **nur sinnvolle Typen** (Dokumente, Text). **Audio, Video und Bilder** werden nicht indiziert. Indizierung kann **synchron**, **asynchron** (Queue) oder als **Batch** erfolgen.
2. **Suche:** Über REST wird eine Lucene-Query übergeben; Antwort ist **paginiert** (from, size, total) und kann **Snippets** (Highlighting) enthalten.
3. **Retention:** Dokumente älter als konfigurierte Tage (z.B. 30) werden automatisch aus dem Index entfernt.

Der **Lucene-Index** liegt persistent auf Platte, nutzt **RAM-Caching** (NRTCachingDirectory) und einen **langlebigen IndexWriter** mit periodischem Commit für hohen Durchsatz.

## Voraussetzungen

- JDK 21
- Maven 3.9+
- MinIO (lokal z.B. per Docker)

## MinIO starten (Docker)

```bash
docker run -d -p 9000:9000 -p 9001:9001 --name minio \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  quay.io/minio/minio server /data --console-address ":9001"
```

Bucket `attachments` anlegen; Attachment-URL z.B.: `http://localhost:9000/attachments/abc-123/document.pdf`

## Konfiguration (Auszug)

- **minio.*** – URL, Access-Key, Secret-Key
- **lucene.index-path** – Persistenter Index-Pfad
- **lucene.cache.*** – RAM-Cache (max-merge-size-mb, max-cached-mb)
- **lucene.commit-interval-sec** – Commit-Intervall (Sekunden) für Batches
- **lucene.retention-days** – Retention in Tagen (0 = aus)
- **lucene.store-content-for-highlight** – true aktiviert Snippet-Highlighting (mehr Speicher)
- **krata.indexing.use-redis** – `true` = Queue in Redis (überlebt Neustarts), `false` = In-Memory
- **krata.indexing.queue-size** – Größe der Async-Queue (z.B. 100000)
- **krata.indexing.threads** – Worker-Threads für Indizierung (z.B. 8)
- **spring.data.redis.*** – Host/Port/Password, wenn use-redis=true
- **krata.api-key** – Optional: X-API-Key erzwingen (leer = aus)
- **resilience4j.ratelimiter.instances** – Rate Limits (index, search)

### Profile

- **default** – In-Memory-Queue, Entwicklung
- **dev** (`--spring.profiles.active=dev`) – Lockere Limits, DEBUG-Logs für `de.krata`
- **prod** (`--spring.profiles.active=prod`) – Redis-Queue, für **Server mit ~128 GB RAM** abgestimmt (großer Lucene-Cache, viele Worker), Umgebungsvariablen (REDIS_HOST, LUCENE_RETENTION_DAYS, etc.)
- **test** – Kleine Queue, MinIO/Lucene-Health aus

### Produktion (128 GB RAM)

Das Prod-Profil setzt u.a.:
- **Lucene:** bis zu 20 GB RAM-Cache für Index-Segmente (`max-cached-mb: 20480`), größere Segmente im Cache (`max-merge-size-mb: 512`)
- **Indizierung:** 24 Worker-Threads, Queue 200.000, höhere Rate-Limits

**JVM-Start** (Beispiel, Heap nutzt einen Teil des RAMs, Rest für OS/Lucene/Redis):

```bash
java -Xms32g -Xmx48g -jar target/krata-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Mit 48 GB Heap bleibt genug RAM für Lucene-Dateicache (~20 GB), OS und ggf. Redis auf demselben Host.

## Build & Start

```bash
mvn clean package
java -jar target/krata-0.0.1-SNAPSHOT.jar
```

Oder: `mvn spring-boot:run`. Die Anwendung läuft auf Port 8080.

## Docker

```bash
docker compose up -d
```

Enthält **MinIO**, **Redis** (für persistente Indizierungs-Queue im Prod-Profil) und **Krata** (Profil `prod`, nutzt Redis).

- **Krata:** http://localhost:8080 (API, Swagger: /swagger-ui.html)
- **MinIO Console:** http://localhost:9001
- **Redis:** Port 6379 (intern)

## Swagger UI

**http://localhost:8080/swagger-ui.html**

## REST-API (Kurzüberblick)

| Methode | Endpoint | Beschreibung |
|--------|----------|--------------|
| POST | /api/attachments/index | Einzel-Indizierung (sync). `?async=true` → 202, Queue |
| POST | /api/attachments/index/batch | Batch-Indizierung (async), 202 Accepted |
| GET | /api/attachments/index/status/{uuid} | Status eines Jobs (PENDING/INDEXED/SKIPPED/FAILED) |
| DELETE | /api/attachments/{uuid} | Einzelnes Dokument aus Index entfernen |
| DELETE | /api/attachments | **Bulk-Delete:** mehrere UUIDs im Body (max. 1000) |
| POST | /api/search | Volltextsuche, paginiert (query, from, size, withHighlight) |

### Suche (Beispiel)

**POST** `/api/search`

```json
{
  "query": "content:lucene",
  "from": 0,
  "size": 20,
  "withHighlight": false
}
```

Antwort: `{ "total": 42, "from": 0, "size": 20, "hits": [ { "attachmentUuid": "...", "snippet": null } ] }`

### Fehlerantworten

Alle Fehler liefern ein einheitliches JSON (`timestamp`, `status`, `error`, `message`, `path`, `code`, ggf. `fields`). Codes z.B.: `VALIDATION_ERROR`, `MINIO_ERROR`, `INDEX_ERROR`, `INVALID_QUERY`, `QUEUE_FULL`, `RATE_LIMIT_EXCEEDED`.

## Health & Betrieb

- **Actuator:** `/actuator/health` (enthält MinIO- und Lucene-Status), `/actuator/info`, `/actuator/metrics`
- **API-Key:** Wenn `krata.api-key` gesetzt ist, muss der Header `X-API-Key` gesendet werden.
- **Rate Limits:** Standard z.B. 1000 Indizierungen/Minute, 300 Suchen/Minute (konfigurierbar).

## Tests & CI

```bash
mvn verify
```

- **Unit-/Controller-Tests** laufen ohne weitere Voraussetzungen.
- **IntegrationTest** (MinIO + Indizierung + Suche) nutzt Testcontainers und **benötigt Docker**. Ohne Docker wird der Test übersprungen. In CI (GitHub Actions mit Docker) läuft er automatisch.

CI (GitHub Actions): `.github/workflows/ci.yml` – Build, Tests (inkl. Integrationstest bei Docker), Docker-Build.

## Technologie-Stack

- **Spring Boot 3.2**, JDK 21, Lombok
- **Apache Lucene 9** – Indexierung, NRT, Retention, Highlighting
- **MinIO Java SDK** – S3-kompatibler Storage
- **Apache Tika** – Textextraktion
- **SpringDoc** – OpenAPI/Swagger
- **Resilience4j** – Rate Limiting
- **Spring Boot Actuator** – Health, Metriken
