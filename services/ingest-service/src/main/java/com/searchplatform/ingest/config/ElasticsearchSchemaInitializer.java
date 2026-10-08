package com.searchplatform.ingest.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class ElasticsearchSchemaInitializer implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchSchemaInitializer.class);
    private static final String INDEX_DEFINITION = "elasticsearch/npm-packages-index.json";

    private final ElasticsearchClient client;
    private final String index;

    public ElasticsearchSchemaInitializer(ElasticsearchClient client, @Value("${elasticsearch.index}") String index) {
        this.client = client;
        this.index = index;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (client.indices().exists(e -> e.index(index)).value()) {
            LOG.info("Elasticsearch index {} already exists", index);
            return;
        }
        try (InputStream definition = new ClassPathResource(INDEX_DEFINITION).getInputStream()) {
            client.indices().create(c -> c.index(index).withJson(definition));
        }
        LOG.info("Created Elasticsearch index {}", index);
    }
}
