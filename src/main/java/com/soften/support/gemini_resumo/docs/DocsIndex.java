package com.soften.support.gemini_resumo.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.*;

/**
 * In-memory BM25 index over the public SoftenDocs pages (docs.softensistemas.com.br),
 * built offline by tools/softendocs and shipped as a classpath resource. Any model can
 * read the passages it returns, so retrieval no longer depends on Gemini File Search.
 */
@Component
public class DocsIndex {

    public record Passage(String title, String book, String product, String url, String updated, String text) {}
    public record Hit(Passage passage, double score, int matchedTerms) {}

    static final String RESOURCE = "/softendocs/index.json";
    private static final Logger log = LoggerFactory.getLogger(DocsIndex.class);
    private static final double K1 = 1.2, B = 0.75, PRODUCT_BOOST = 1.35;
    private static final int TITLE_WEIGHT = 3;
    private static final Set<String> STOP = Set.of(
            "a", "o", "as", "os", "um", "uma", "uns", "umas", "de", "do", "da", "dos", "das", "no", "na", "nos", "nas",
            "em", "por", "para", "pra", "pro", "com", "sem", "e", "ou", "que", "se", "ao", "aos", "como", "mais", "mas",
            "ja", "nao", "sim", "eu", "voce", "ele", "ela", "isso", "esse", "essa", "este", "esta", "aqui", "la", "me",
            "meu", "minha", "seu", "sua", "tem", "ter", "foi", "ser", "estou", "estao", "quando", "qual",
            "quais", "onde", "porque", "pq", "tambem", "ok", "oi", "ola", "bom", "boa", "dia", "tarde", "noite",
            "obrigado", "obrigada", "favor", "cliente", "consigo", "conseguir", "preciso", "fazer", "faco", "faz",
            "ai", "entao", "certo", "tudo", "bem", "vc", "so", "ate", "lo", "lhe", "nesse", "nessa", "dele");

    private final List<Passage> passages = new ArrayList<>();
    private final List<Map<String, Integer>> termFreqs = new ArrayList<>();
    private final int[] lengths;
    private final Map<String, Integer> docFreq = new HashMap<>();
    private final double avgLength;
    private final String builtAt;

    @Autowired
    public DocsIndex(ObjectMapper mapper) {
        this(load(mapper));
    }

    public DocsIndex(JsonNode root) {
        builtAt = root == null ? "" : root.path("builtAt").asText("");
        if (root != null) {
            for (JsonNode c : root.path("chunks")) {
                passages.add(new Passage(c.path("t").asText(), c.path("b").asText(), c.path("p").asText(),
                        c.path("u").asText(), c.path("d").asText(), c.path("x").asText()));
            }
        }
        lengths = new int[passages.size()];
        long total = 0;
        for (int i = 0; i < passages.size(); i++) {
            Passage p = passages.get(i);
            Map<String, Integer> tf = new HashMap<>();
            List<String> titleTerms = terms(p.title() + " " + p.book().replace('-', ' '));
            for (int w = 0; w < TITLE_WEIGHT; w++) titleTerms.forEach(t -> tf.merge(t, 1, Integer::sum));
            terms(p.text()).forEach(t -> tf.merge(t, 1, Integer::sum));
            tf.keySet().forEach(t -> docFreq.merge(t, 1, Integer::sum));
            int len = tf.values().stream().mapToInt(Integer::intValue).sum();
            lengths[i] = len;
            total += len;
            termFreqs.add(tf);
        }
        avgLength = passages.isEmpty() ? 1 : (double) total / passages.size();
        log.info("SoftenDocs index loaded. passages={}, builtAt={}", passages.size(), builtAt);
    }

    private static JsonNode load(ObjectMapper mapper) {
        try (InputStream in = DocsIndex.class.getResourceAsStream(RESOURCE)) {
            return in == null ? null : mapper.readTree(in);
        } catch (Exception e) {
            log.error("SoftenDocs index could not be read.", e);
            return null;
        }
    }

    public int size() { return passages.size(); }
    public String builtAt() { return builtAt; }

    /**
     * Best passages for the query, at most two per page. A product named in the query
     * (or passed explicitly) ranks that product's pages first without hiding the others.
     */
    public List<Hit> search(String query, String product, int limit) {
        List<String> queryTerms = new ArrayList<>(new LinkedHashSet<>(terms(query)));
        if (queryTerms.isEmpty() || passages.isEmpty()) return List.of();
        String wanted = product == null || product.isBlank() ? detectProduct(query) : product;

        List<Hit> scored = new ArrayList<>();
        for (int i = 0; i < passages.size(); i++) {
            Map<String, Integer> tf = termFreqs.get(i);
            double score = 0;
            int matched = 0;
            for (String term : queryTerms) {
                Integer f = tf.get(term);
                if (f == null) continue;
                matched++;
                double idf = Math.log(1 + (passages.size() - docFreq.get(term) + 0.5) / (docFreq.get(term) + 0.5));
                score += idf * (f * (K1 + 1)) / (f + K1 * (1 - B + B * lengths[i] / avgLength));
            }
            if (score <= 0) continue;
            if (wanted != null && wanted.equals(passages.get(i).product())) score *= PRODUCT_BOOST;
            scored.add(new Hit(passages.get(i), score, matched));
        }
        scored.sort(Comparator.comparingDouble(Hit::score).reversed());

        List<Hit> out = new ArrayList<>();
        Map<String, Integer> perPage = new HashMap<>();
        for (Hit hit : scored) {
            if (perPage.merge(hit.passage().url(), 1, Integer::sum) > 2) continue;
            out.add(hit);
            if (out.size() >= limit) break;
        }
        return out;
    }

    static String detectProduct(String text) {
        String n = normalize(text);
        if (n.matches("(?s).*\\bsiem\\b.*")) return "Soften SIEM";
        if (n.matches("(?s).*\\bgerencie ?aqui\\b.*")) return "GerencieAqui";
        if (n.matches("(?s).*\\bsoften ?food\\b.*")) return "SoftenFood";
        return null;
    }

    static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    static List<String> terms(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : normalize(text).split("[^a-z0-9]+")) {
            if (raw.length() < 2 || STOP.contains(raw)) continue;
            out.add(stem(raw));
        }
        return out;
    }

    // Light Portuguese stemming: plural and a few verb endings, never on codes such as "nfe" or "539".
    static String stem(String t) {
        if (t.length() <= 4 || Character.isDigit(t.charAt(0))) return t;
        if (t.endsWith("oes") || t.endsWith("aes")) return t.substring(0, t.length() - 3) + "ao";
        if (t.endsWith("ais")) return t.substring(0, t.length() - 3) + "al";
        if (t.endsWith("eis") && t.length() > 5) return t.substring(0, t.length() - 3) + "el";
        if (t.endsWith("ns")) return t.substring(0, t.length() - 2) + "m";
        if (t.endsWith("ar") || t.endsWith("er") || t.endsWith("ir")) return t.substring(0, t.length() - 2);
        if (t.endsWith("s")) return t.substring(0, t.length() - 1);
        return t;
    }
}
