package com.searchplatform.search.service.npm;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.searchplatform.model.search.NpmPackageDocument;
import com.searchplatform.search.service.shared.SearchServiceResponse;
import com.searchplatform.search.service.shared.SearchServiceResponseItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class NpmSearchService {
    private static final Logger LOG = LoggerFactory.getLogger(NpmSearchService.class);

    private final String index;
    private final ElasticsearchClient client;

    public NpmSearchService(ElasticsearchClient client, @Value("${elasticsearch.index}") String index) {
        this.index = index;
        this.client = client;
    }

    public SearchServiceResponse<NpmPackageSearchResponse> query(String query, int page, int pageSize) {
        try {
            // Exact (case-insensitive) package name outranks everything else; the multi_match below handles the rest.
            Query exactName = new Query.Builder().term(new TermQuery.Builder()
                    .field("name.keyword").value(query).caseInsensitive(true).boost(10f).build()).build();
            Query fuzzyText = new Query.Builder().multiMatch(new MultiMatchQuery.Builder()
                    .fields(List.of("name", "keywords.text", "description"))
                    .query(query)
                    .operator(Operator.And)
                    .fuzziness("AUTO")
                    .build()).build();
            Query searchQuery = new Query.Builder().bool(new BoolQuery.Builder()
                    .should(exactName, fuzzyText)
                    .minimumShouldMatch("1")
                    .build()).build();
            SearchResponse<NpmPackageDocument> response = client.search(new SearchRequest.Builder().index(index).query(searchQuery).from((page - 1) * pageSize).size(pageSize).trackTotalHits(t -> t.enabled(true)).build(), NpmPackageDocument.class);

            return parseResponse(response);
        } catch (IOException | ElasticsearchException e) {
            //basic log and rethrow for now
            LOG.error("Error while making search query.", e);
            throw new RuntimeException(e);
        }
    }

    private SearchServiceResponse<NpmPackageSearchResponse> parseResponse(SearchResponse<NpmPackageDocument> response) {
        List<SearchServiceResponseItem<NpmPackageSearchResponse>> documents = new ArrayList<>();
        long totalResults = 0;
        if (response != null && response.hits() != null && response.hits().hits() != null) {
            if (response.hits().total() != null) {
                totalResults = response.hits().total().value();
            }
            for (Hit<NpmPackageDocument> hit : response.hits().hits()) {
                if (hit.source() == null) continue;
                NpmPackageSearchResponse source = mapToPackageSearchDocument(hit.source());
                //add metadata under attributes but for now leave it null.
                documents.add(new SearchServiceResponseItem<>(hit.id(), source, null));
            }
        }
        return new SearchServiceResponse<>(documents, Math.max(totalResults, documents.size()));
    }

    // For now the response is the same as the document but this give us decoupling if needed.
    private NpmPackageSearchResponse mapToPackageSearchDocument(NpmPackageDocument document) {
        return new NpmPackageSearchResponse(
                document.name(),
                document.description(),
                document.keywords(),
                document.latestVersion(),
                document.license(),
                document.datePackageCreated(),
                document.datePackageModified(),
                document.dateLatestVersionModified());
    }
}
