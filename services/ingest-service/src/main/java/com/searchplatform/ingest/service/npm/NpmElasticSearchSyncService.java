package com.searchplatform.ingest.service.npm;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.searchplatform.ingest.domain.npm.NpmPackage;
import com.searchplatform.ingest.service.exceptions.ElasticSearchSyncRequestException;
import com.searchplatform.model.search.NpmPackageDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/*
  Small note here. I know spring data has annotations, repository, and other nice things to make this simple.
  I am doing things at a lower level right now for a bit of a better understanding and learning through suffering.
  Will likely re-write all this later so that is a problem for future me.
 */

@Service
public class NpmElasticSearchSyncService {
    private static final Logger LOG = LoggerFactory.getLogger(NpmElasticSearchSyncService.class);

    private final ElasticsearchClient elasticsearchClient;
    private final String index;

    public NpmElasticSearchSyncService(ElasticsearchClient elasticsearchClient, @Value("${elasticsearch.index}") String index) {
        this.elasticsearchClient = elasticsearchClient;
        this.index = index;
    }

    public void updateNpmPackages(List<NpmPackage> packagesToUpdate) {
        if (packagesToUpdate == null || packagesToUpdate.isEmpty()) return;

        try {
            List<BulkOperation> operations = packagesToUpdate.stream()
                    .map(pkg -> BulkOperation.of(b -> b.index(i -> i
                            .id(pkg.getPackageId())
                            .document(toDocument(pkg))))).toList();

            BulkResponse response = elasticsearchClient.bulk(b -> b.index(index).operations(operations));

            boolean hasError = false;
            for (BulkResponseItem item : response.items()) {
                int status = item.status();
                if (status == 200 || status == 201) continue;
                hasError = true;
                LOG.error("Failed to index package with id:{} with status:{} and error:{}", item.id(), status, item.error() != null ? item.error().reason() : "unknown error");
            }
            if (hasError) throw new ElasticSearchSyncRequestException("Partial error indexing packages");
        } catch (IOException | ElasticsearchException e) {
            LOG.error("Failed to update packages: ", e);
            throw new ElasticSearchSyncRequestException("Error with sync request", e);
        }
    }

    public void removeNpmPackages(List<String> npmPackagesToRemove) {
        if (npmPackagesToRemove == null || npmPackagesToRemove.isEmpty()) return;

        try {
            List<BulkOperation> operations = npmPackagesToRemove.stream()
                    .map(id -> BulkOperation.of(b -> b.delete(d -> d.id(id))))
                    .toList();

            BulkResponse response = elasticsearchClient.bulk(b -> b.index(index).operations(operations));

            boolean hasError = false;
            for (BulkResponseItem item : response.items()) {
                int status = item.status();
                if (status == 200 || status == 404) continue;
                hasError = true;
                LOG.error("Failed to delete package with id:{} failed with status:{} and error:{}", item.id(), status, item.error() != null ? item.error().reason() : "unknown error");
            }
            if (hasError) throw new ElasticSearchSyncRequestException("Partial error removing packages");

        } catch (IOException | ElasticsearchException e) {
            LOG.error("Failed to remove packages: ", e);
            throw new ElasticSearchSyncRequestException("Error with sync request", e);
        }
    }

    private NpmPackageDocument toDocument(NpmPackage toMap) {
        return new NpmPackageDocument(
                toMap.getName(),
                toMap.getDescription(),
                toMap.getKeywords(),
                toMap.getLatestVersion(),
                toMap.getLicense(),
                toMap.getDatePackageCreated(),
                toMap.getDatePackageModified(),
                toMap.getDateLatestVersionModified());
    }
}
