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

    public static final String FIELD_ATTACHMENT_UUID = "attachment_uuid";
    public static final String FIELD_CONTENT = "content";
    public static final String FIELD_INDEXED_AT = "indexed_at";

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
        log.info("Lucene-Index initialisiert (persistent, RAM-Cache): {}", path);
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
    public void indexDocument(String attachmentUuid, String content) throws IOException {
        long now = System.currentTimeMillis();
        Document doc = new Document();
        doc.add(new StringField(FIELD_ATTACHMENT_UUID, attachmentUuid, Field.Store.YES));
        String text = content != null ? content : "";
        doc.add(new TextField(FIELD_CONTENT, text, Field.Store.NO));
        doc.add(new LongPoint(FIELD_INDEXED_AT, now));
        doc.add(new StoredField(FIELD_INDEXED_AT, now));
        if (luceneConfig.isStoreContentForHighlight()) {
            doc.add(new StoredField(FIELD_CONTENT + "_stored", text));
        }

        writerLock.lock();
        try {
            indexWriter.addDocument(doc);
        } finally {
            writerLock.unlock();
        }
        log.trace("Dokument eingereiht: attachment_uuid={}", attachmentUuid);
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
     */
    public PaginatedSearchResponse search(String queryString, int from, int size, boolean withHighlight) throws Exception {
        IndexSearcher searcher = searcherManager.acquire();
        try {
            QueryParser parser = new QueryParser(FIELD_CONTENT, analyzer);
            Query query = parser.parse(queryString);
            int maxHits = from + size;
            TopDocs topDocs = searcher.search(query, maxHits);

            long total = topDocs.totalHits.value;
            List<SearchResultHit> hits = new ArrayList<>();
            int end = Math.min(from + size, topDocs.scoreDocs.length);
            String contentFieldForHighlight = luceneConfig.isStoreContentForHighlight() ? FIELD_CONTENT + "_stored" : null;

            for (int i = from; i < end; i++) {
                ScoreDoc scoreDoc = topDocs.scoreDocs[i];
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String uuid = doc.get(FIELD_ATTACHMENT_UUID);
                String snippet = null;
                if (withHighlight && uuid != null && contentFieldForHighlight != null) {
                    snippet = highlight(searcher, query, scoreDoc.doc, contentFieldForHighlight);
                }
                hits.add(SearchResultHit.builder()
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
        PaginatedSearchResponse r = search(queryString, 0, 1000, false);
        return r.getHits().stream()
                .map(SearchResultHit::getAttachmentUuid)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private String highlight(IndexSearcher searcher, Query query, int docId, String fieldName) {
        try {
            Highlighter highlighter = new Highlighter(
                    new SimpleHTMLFormatter("<em>", "</em>"),
                    new QueryScorer(query));
            highlighter.setTextFragmenter(new SimpleSpanFragmenter(new QueryScorer(query), 200));
            Document doc = searcher.storedFields().document(docId);
            String content = doc.get(fieldName);
            if (content == null) return null;
            String[] fragments = highlighter.getBestFragments(analyzer, fieldName, content, 3);
            return fragments.length > 0 ? String.join(" ... ", fragments) : null;
        } catch (Exception e) {
            log.trace("Highlight fehlgeschlagen: {}", e.getMessage());
            return null;
        }
    }
}
