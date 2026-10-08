package com.searchplatform.ingest.service.npm;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.searchplatform.ingest.config.ElasticsearchSchemaInitializer;
import com.searchplatform.ingest.domain.npm.NpmPackage;
import com.searchplatform.model.search.NpmPackageDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/*
  Round trip against a real Elasticsearch: the schema initializer creates the index from
  npm-packages-index.json (dynamic: strict), then the sync service writes through it.
  Only the three beans under test plus the ES client auto-configuration are loaded, so no
  Postgres or web server is needed.
 */
@SpringBootTest(
        classes = {ElasticsearchSchemaInitializer.class, NpmElasticSearchSyncService.class},
        properties = "elasticsearch.index=it-npm-packages")
@ImportAutoConfiguration({JacksonAutoConfiguration.class, ElasticsearchRestClientAutoConfiguration.class, ElasticsearchClientAutoConfiguration.class})
@Testcontainers
class NpmElasticSearchSyncServiceIT {

    // Same major as compose.yaml and the client library; security off like the local stack.
    @Container
    static final ElasticsearchContainer elasticsearch =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:9.5.2")
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", () -> "http://" + elasticsearch.getHttpHostAddress());
    }

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    NpmElasticSearchSyncService service;

    @Autowired
    ElasticsearchClient client;

    @Value("${elasticsearch.index}")
    String index;

    @Test
    void indexedPackagesAreSearchableAndRoundTripEveryField() throws IOException {
        service.updateNpmPackages(List.of(
                pkg("@scope/deno-adapter", "0.7.0", "Deno runtime adapter", List.of("deno", "adapter")),
                pkg("left-pad", "1.3.0", "Pads strings on the left", List.of("string", "pad"))));
        refresh();

        var hits = client.search(s -> s.index(index).query(q -> q.match(m -> m.field("description").query("deno"))),
                NpmPackageDocument.class).hits().hits();

        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().id()).isEqualTo("@scope/deno-adapter");
        NpmPackageDocument doc = hits.getFirst().source();
        assertThat(doc.name()).isEqualTo("@scope/deno-adapter");
        assertThat(doc.latestVersion()).isEqualTo("0.7.0");
        assertThat(doc.license()).isEqualTo("MIT");
        assertThat(doc.keywords()).containsExactly("deno", "adapter");
        assertThat(doc.datePackageCreated().toInstant()).isEqualTo(T0.toInstant());
        assertThat(doc.datePackageModified().toInstant()).isEqualTo(T0.plusDays(1).toInstant());
        assertThat(doc.dateLatestVersionModified().toInstant()).isEqualTo(T0.plusDays(2).toInstant());
    }

    @Test
    void reindexingTheSamePackageReplacesTheDocumentInsteadOfDuplicatingIt() throws IOException {
        service.updateNpmPackages(List.of(pkg("replace-me", "1.0.0", "first", List.of("a"))));
        service.updateNpmPackages(List.of(pkg("replace-me", "2.0.0", "second", List.of("a", "b"))));
        refresh();

        assertThat(countWithId("replace-me")).isEqualTo(1);
        NpmPackageDocument doc = get("replace-me");
        assertThat(doc.latestVersion()).isEqualTo("2.0.0");
        assertThat(doc.description()).isEqualTo("second");
        assertThat(doc.keywords()).containsExactly("a", "b");
    }

    @Test
    void removedPackagesDisappearAndUnknownIdsAreIgnored() throws IOException {
        service.updateNpmPackages(List.of(pkg("keep", "1.0.0", "kept", null), pkg("drop", "1.0.0", "dropped", null)));
        refresh();

        service.removeNpmPackages(List.of("drop", "never-indexed"));
        refresh();

        assertThat(client.exists(e -> e.index(index).id("drop")).value()).isFalse();
        assertThat(client.exists(e -> e.index(index).id("keep")).value()).isTrue();
    }

    @Test
    void indexIsCreatedFromTheStrictMapping() throws IOException {
        var mappings = client.indices().getMapping(m -> m.index(index)).get(index).mappings();

        assertThat(mappings.dynamic()).isNotNull();
        assertThat(mappings.dynamic().jsonValue()).isEqualTo("strict");
    }

    private void refresh() throws IOException {
        client.indices().refresh(r -> r.index(index));
    }

    private long countWithId(String id) throws IOException {
        return client.count(c -> c.index(index).query(q -> q.ids(i -> i.values(id)))).count();
    }

    private NpmPackageDocument get(String id) throws IOException {
        GetResponse<NpmPackageDocument> response = client.get(g -> g.index(index).id(id), NpmPackageDocument.class);
        assertThat(response.found()).isTrue();
        return response.source();
    }

    private static NpmPackage pkg(String id, String version, String description, List<String> keywords) {
        NpmPackage p = new NpmPackage();
        p.setPackageId(id);
        p.setName(id);
        p.setLatestVersion(version);
        p.setDescription(description);
        p.setKeywords(keywords);
        p.setLicense("MIT");
        p.setDatePackageCreated(T0);
        p.setDatePackageModified(T0.plusDays(1));
        p.setDateLatestVersionModified(T0.plusDays(2));
        return p;
    }
}
