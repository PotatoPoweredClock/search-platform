package com.searchplatform.ingest.config;

import com.searchplatform.model.search.NpmPackageDocument;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/*
  The mapping is dynamic: strict, so a field on NpmPackageDocument that is missing from the JSON
  (or the reverse) turns into per-item 400s during ingest. Catch the drift here, without a container.
 */
class ElasticsearchIndexDefinitionTest {

    @Test
    void mappingPropertiesMatchTheDocumentRecordExactly() throws IOException {
        JsonNode definition;
        try (InputStream in = new ClassPathResource("elasticsearch/npm-packages-index.json").getInputStream()) {
            definition = JsonMapper.builder().build().readTree(in);
        }

        Set<String> mapped = new TreeSet<>(definition.path("mappings").path("properties").propertyNames());
        Set<String> documentFields = new TreeSet<>(Arrays.stream(NpmPackageDocument.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList());

        assertThat(definition.path("mappings").path("dynamic").asString()).isEqualTo("strict");
        assertThat(mapped).isEqualTo(documentFields);
    }
}
