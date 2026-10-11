package com.searchplatform.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/*
  Wiring smoke test: the full application context starts against a real Elasticsearch and the
  auto-configured ElasticsearchClient can talk to it. Does not create the index; ingest-service owns that.
 */
@SpringBootTest
@Testcontainers
class SearchApiApplicationIT {

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

    @Autowired
    ElasticsearchClient client;

    @Value("${elasticsearch.index}")
    String index;

    @Test
    void contextStartsAndClientReachesElasticsearch() throws IOException {
        assertThat(client.ping().value()).isTrue();
        assertThat(index).isEqualTo("npm-packages");
    }
}
