package de.krata.service;

import de.krata.config.LuceneConfig;
import de.krata.dto.PaginatedSearchResponse;
import de.krata.dto.SearchResultHit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NRTCachingDirectory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lucene-Indexierung und Volltextsuche.
 * Langlebiger IndexWriter für hohen Durchsatz (50k+ Dokumente/Tag), NRT-Suche, Retention, Paginierung, optional Highlighting.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LuceneIndexService {

    public static final String FIELD_RECORD_UUID = "record_uuid";
    public static final String FIELD_ATTACHMENT_UUID = "attachment_uuid";
    public static final String FIELD_CONTENT = "content";
    public static final String FIELD_INDEXED_AT = "indexed_at";
    /** Anwender-Erstellungszeitpunkt (kann von der Indizierungszeit abweichen). */
    public static final String FIELD_DOCUMENT_CREATED_AT = "document_created_at";

    private final LuceneConfig luceneConfig;
    private final StandardAnalyzer analyzer = new StandardAnalyzer();

    private Directory directory;
    private IndexWriter indexWriter;
    private SearcherManager searcherManager;
    private final ReentrantLock writerLock = new ReentrantLock();

    @jakarta.annotation.PostConstruct
    public void init() throws IOException {
        var path = luceneConfig.getIndexPath();
        Files.createDirectories(path);
        Directory fsDir = FSDirectory.open(path);
        directory = new NRTCachingDirectory(
                fsDir,
                luceneConfig.getMaxMergeSizeMb(),
                luceneConfig.getMaxCachedMb()
        );
        IndexWriterConfig writerConfig = new IndexWriterConfig(analyzer);
        writerConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        writerConfig.setCommitOnClose(true);
        indexWriter = new IndexWriter(directory, writerConfig);
        searcherManager = new SearcherManager(indexWriter, null);
        log.info("Lucene-Index initialisiert (persistent, RAM-Cache): {}, storeContentForHighlight={}", path, luceneConfig.isStoreContentForHighlight());
    }

    @Scheduled(cron = "${lucene.retention-cron:0 0 2 * * ?}")
    public void scheduledRetention() {
        runRetention();
    }

    @Scheduled(fixedDelayString = "${lucene.commit-interval-sec:3}000")
    public void commitAndRefresh() {
        try {
            writerLock.lock();
            if (indexWriter != null) {
                indexWriter.commit();
                searcherManager.maybeRefresh();
            }
        } catch (IOException e) {
            log.warn("Lucene commit/refresh fehlgeschlagen: {}", e.getMessage());
        } finally {
            writerLock.unlock();
        }
    }

    @PreDestroy
    public void close() throws IOException {
        try {
            if (searcherManager != null) searcherManager.close();
            if (indexWriter != null) indexWriter.close();
            if (directory != null) directory.close();
        } finally {
            indexWriter = null;
            searcherManager = null;
            directory = null;
        }
    }

    /**
     * Fügt ein Dokument zum Index hinzu (Commit erfolgt im Scheduled-Job für Durchsatz).
     */
    /**
     * @param documentCreatedAt Anwender-Erstellungszeit; {@code null} → es wird der Indizierungszeitpunkt verwendet
     */
    public void indexDocument(String recordUuid, String attachmentUuid, String content, Instant documentCreatedAt) throws IOException {
        long now = System.currentTimeMillis();
        long createdEpoch = documentCreatedAt != null ? documentCreatedAt.toEpochMilli() : now;
        Document doc = new Document();
        doc.add(new StringField(FIELD_RECORD_UUID, recordUuid, Field.Store.YES));
        doc.add(new StringField(FIELD_ATTACHMENT_UUID, attachmentUuid, Field.Store.YES));
        String text = content != null ? content : "";
        doc.add(new TextField(FIELD_CONTENT, text, Field.Store.NO));
        doc.add(new LongPoint(FIELD_INDEXED_AT, now));
        doc.add(new StoredField(FIELD_INDEXED_AT, now));
        doc.add(new LongPoint(FIELD_DOCUMENT_CREATED_AT, createdEpoch));
        doc.add(new StoredField(FIELD_DOCUMENT_CREATED_AT, createdEpoch));
        if (luceneConfig.isStoreContentForHighlight()) {
            doc.add(new StoredField(FIELD_CONTENT + "_stored", text));
        }

        writerLock.lock();
        try {
            // Upsert nach attachment_uuid: verhindert Duplikate bei Re-Indexing und stellt sicher,
            // dass neue Stored-Fields (für Highlighting) wirksam werden.
            indexWriter.updateDocument(new Term(FIELD_ATTACHMENT_UUID, attachmentUuid), doc);
        } finally {
            writerLock.unlock();
        }
        log.trace("Dokument eingereiht: record_uuid={}, attachment_uuid={}", recordUuid, attachmentUuid);
    }

    /**
     * Entfernt das Dokument mit der angegebenen attachment_uuid aus dem Index.
     */
    public void deleteByAttachmentUuid(String attachmentUuid) throws IOException {
        Term term = new Term(FIELD_ATTACHMENT_UUID, attachmentUuid);
        writerLock.lock();
        try {
            indexWriter.deleteDocuments(term);
        } finally {
            writerLock.unlock();
        }
        log.info("Dokument gelöscht: attachment_uuid={}", attachmentUuid);
    }

    /**
     * Entfernt alle Dokumente mit den angegebenen attachment_uuids aus dem Index.
     */
    public void deleteByAttachmentUuids(java.util.List<String> attachmentUuids) throws IOException {
        if (attachmentUuids == null || attachmentUuids.isEmpty()) return;
        Term[] terms = attachmentUuids.stream()
                .map(uuid -> new Term(FIELD_ATTACHMENT_UUID, uuid))
                .toArray(Term[]::new);
        writerLock.lock();
        try {
            indexWriter.deleteDocuments(terms);
        } finally {
            writerLock.unlock();
        }
        log.info("Bulk-Delete: {} Dokumente aus Index entfernt", attachmentUuids.size());
    }

    /**
     * Löscht alle Dokumente, deren indexed_at vor dem angegebenen Zeitpunkt liegt (Retention).
     */
    public int deleteOlderThan(long cutoffEpochMs) throws IOException {
        Query rangeQuery = LongPoint.newRangeQuery(FIELD_INDEXED_AT, 0L, cutoffEpochMs);
        writerLock.lock();
        try {
            indexWriter.deleteDocuments(rangeQuery);
            return 0; // Lucene gibt Anzahl nicht direkt zurück; wir loggen nur
        } finally {
            writerLock.unlock();
        }
    }

    /**
     * Führt Retention aus: löscht Dokumente älter als retention-days.
     */
    public void runRetention() {
        int days = luceneConfig.getRetentionDays();
        if (days <= 0) return;
        long cutoff = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);
        try {
            deleteOlderThan(cutoff);
            log.info("Retention ausgeführt: Dokumente älter als {} Tage entfernt", days);
        } catch (IOException e) {
            log.error("Retention fehlgeschlagen: {}", e.getMessage());
        }
    }

    /**
     * Durchsucht den Index mit Paginierung und optionalem Highlighting.
     * Optional: Zeitfenster auf {@link #FIELD_DOCUMENT_CREATED_AT} (Anwender-Erstellungszeit).
     */
    public PaginatedSearchResponse search(
            String queryString,
            int from,
            int size,
            boolean withHighlight,
            Instant createdFrom,
            Instant createdTo) throws Exception {
        IndexSearcher searcher = searcherManager.acquire();
        try {
            QueryParser parser = new QueryParser(FIELD_CONTENT, analyzer);
            Query contentQuery = parser.parse(queryString);
            Query query = combineWithTimeWindow(contentQuery, createdFrom, createdTo);
            int maxHits = from + size;
            TopDocs topDocs = searcher.search(query, maxHits);

            long total = topDocs.totalHits.value;
            List<SearchResultHit> hits = new ArrayList<>();
            int end = Math.min(from + size, topDocs.scoreDocs.length);
            String storedContentField = luceneConfig.isStoreContentForHighlight() ? FIELD_CONTENT + "_stored" : null;

            for (int i = from; i < end; i++) {
                ScoreDoc scoreDoc = topDocs.scoreDocs[i];
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String recordUuid = doc.get(FIELD_RECORD_UUID);
                String uuid = doc.get(FIELD_ATTACHMENT_UUID);
                String snippet = null;
                if (withHighlight && uuid != null && storedContentField != null) {
                    // Query wird auf FIELD_CONTENT geparsed, der Text liegt aber in einem StoredField.
                    snippet = highlight(searcher, contentQuery, scoreDoc.doc, storedContentField, FIELD_CONTENT);
                }
                hits.add(SearchResultHit.builder()
                        .recordUuid(recordUuid)
                        .attachmentUuid(uuid)
                        .snippet(snippet)
                        .build());
            }

            return PaginatedSearchResponse.builder()
                    .total(total)
                    .from(from)
                    .size(size)
                    .hits(hits)
                    .build();
        } finally {
            searcherManager.release(searcher);
        }
    }

    /** Einfache Suche ohne Paginierung (Legacy): gibt UUIDs zurück. */
    public List<String> search(String queryString) throws Exception {
        PaginatedSearchResponse r = search(queryString, 0, 1000, false, null, null);
        return r.getHits().stream()
                .map(SearchResultHit::getAttachmentUuid)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Kombiniert die Volltext-Query mit einem Zeitfenster auf document_created_at (beide Grenzen inklusiv).
     */
    private Query combineWithTimeWindow(Query contentQuery, Instant createdFrom, Instant createdTo) {
        if (createdFrom == null && createdTo == null) {
            return contentQuery;
        }
        long min = createdFrom != null ? createdFrom.toEpochMilli() : Long.MIN_VALUE;
        long max = createdTo != null ? createdTo.toEpochMilli() : Long.MAX_VALUE;
        Query timeQuery = LongPoint.newRangeQuery(FIELD_DOCUMENT_CREATED_AT, min, max);
        BooleanQuery.Builder b = new BooleanQuery.Builder();
        b.add(contentQuery, BooleanClause.Occur.MUST);
        b.add(timeQuery, BooleanClause.Occur.MUST);
        return b.build();
    }

    private String highlight(IndexSearcher searcher, Query query, int docId, String storedFieldName, String analysisFieldName) {
        try {
            // Wichtig für Wildcards/Prefix/Fuzzy (MultiTermQuery): rewrite, damit QueryScorer passende Terme sieht.
            Query rewritten = query.rewrite(searcher.getIndexReader());
            QueryScorer scorer = new QueryScorer(rewritten, analysisFieldName);
            Highlighter highlighter = new Highlighter(
                    new SimpleHTMLFormatter("<em>", "</em>"),
                    scorer);
            // Wichtig: denselben Scorer fürs Fragmenting nutzen, sonst kann intern init() fehlen → NPE.
            highlighter.setTextFragmenter(new SimpleSpanFragmenter(scorer, 200));
            Document doc = searcher.storedFields().document(docId);
            String content = doc.get(storedFieldName);
            if (content == null) {
                log.warn("Highlight nicht möglich: StoredField fehlt (docId={}, storedField={})", docId, storedFieldName);
                return null;
            }
            // Wichtig: analysisFieldName muss zum Feldnamen in der Query passen, sonst matcht der Scorer nichts.
            String[] fragments = highlighter.getBestFragments(analyzer, analysisFieldName, content, 3);
            if (fragments.length == 0) {
                log.info("Kein Highlight-Fragment gefunden (docId={}, query={}, analysisField={})", docId, query, analysisFieldName);
                return null;
            }
            return String.join(" ... ", fragments);
        } catch (Exception e) {
            // In prod ist TRACE nicht sichtbar; WARN hilft bei der Diagnose.
            log.warn("Highlight fehlgeschlagen (docId={}): {}", docId, e.toString());
            return null;
        }
    }
}
