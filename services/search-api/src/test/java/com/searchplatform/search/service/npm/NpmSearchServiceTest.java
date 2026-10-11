package com.searchplatform.search.service.npm;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import com.searchplatform.model.search.NpmPackageDocument;
import com.searchplatform.search.service.shared.SearchServiceResponse;
import com.searchplatform.search.service.shared.SearchServiceResponseItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NpmSearchServiceTest {
    private static final String INDEX = "npm-packages";
    private static final OffsetDateTime TS = OffsetDateTime.of(2024, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC);

    @Mock
    private ElasticsearchClient client;

    private NpmSearchService service;

    @BeforeEach
    void setUp() {
        service = new NpmSearchService(client, INDEX);
    }

    // ---- request building ----

    @Test
    void searchesTheConfiguredIndex() throws Exception {
        SearchRequest request = runAndCaptureRequest("express", 1, 20);

        assertThat(request.index()).containsExactly(INDEX);
    }

    @Test
    void buildsExactNameBoostAndFuzzyMultiMatchAsShouldClauses() throws Exception {
        SearchRequest request = runAndCaptureRequest("express", 1, 20);

        BoolQuery bool = request.query().bool();
        assertThat(bool.minimumShouldMatch()).isEqualTo("1");
        assertThat(bool.should()).hasSize(2);

        TermQuery exact = bool.should().get(0).term();
        assertThat(exact.field()).isEqualTo("name.keyword");
        assertThat(exact.value().stringValue()).isEqualTo("express");
        assertThat(exact.caseInsensitive()).isTrue();
        assertThat(exact.boost()).isEqualTo(10f);

        MultiMatchQuery multi = bool.should().get(1).multiMatch();
        assertThat(multi.query()).isEqualTo("express");
        assertThat(multi.fields()).containsExactlyInAnyOrder("name", "keywords.text", "description");
        assertThat(multi.operator()).isEqualTo(Operator.And);
        assertThat(multi.fuzziness()).isEqualTo("AUTO");
    }

    @Test
    void passesTheQueryStringThroughUnchanged() throws Exception {
        SearchRequest request = runAndCaptureRequest("@types/node", 1, 20);

        assertThat(request.query().bool().should().get(0).term().value().stringValue()).isEqualTo("@types/node");
        assertThat(request.query().bool().should().get(1).multiMatch().query()).isEqualTo("@types/node");
    }

    @Test
    void firstPageStartsAtOffsetZero() throws Exception {
        SearchRequest request = runAndCaptureRequest("express", 1, 20);

        assertThat(request.from()).isZero();
        assertThat(request.size()).isEqualTo(20);
    }

    @Test
    void laterPagesOffsetByPageSize() throws Exception {
        SearchRequest request = runAndCaptureRequest("express", 3, 10);

        assertThat(request.from()).isEqualTo(20);
        assertThat(request.size()).isEqualTo(10);
    }

    @Test
    void requestsAnExactTotalCount() throws Exception {
        SearchRequest request = runAndCaptureRequest("express", 1, 20);

        assertThat(request.trackTotalHits()).isNotNull();
        assertThat(request.trackTotalHits().enabled()).isTrue();
    }

    // ---- response mapping ----

    @Test
    void mapsHitsToItemsWithIdAndSource() throws Exception {
        stubResponse(total(1), hit("express", document("express")));

        SearchServiceResponse<NpmPackageSearchResponse> response = service.query("express", 1, 20);

        assertThat(response.items()).hasSize(1);
        SearchServiceResponseItem<NpmPackageSearchResponse> item = response.items().getFirst();
        assertThat(item.id()).isEqualTo("express");
        assertThat(item.attributes()).isNull();
        assertThat(item.source()).isEqualTo(new NpmPackageSearchResponse(
                "express", "express description", List.of("web", "framework"), "5.0.0", "MIT", TS, TS, TS));
    }

    @Test
    void keepsHitOrder() throws Exception {
        stubResponse(total(3), hit("a", document("a")), hit("b", document("b")), hit("c", document("c")));

        SearchServiceResponse<NpmPackageSearchResponse> response = service.query("pkg", 1, 20);

        assertThat(response.items()).extracting(SearchServiceResponseItem::id).containsExactly("a", "b", "c");
    }

    @Test
    void totalResultsIsTheMatchCountAcrossAllPagesNotThePageSize() throws Exception {
        stubResponse(total(4321), hit("a", document("a")), hit("b", document("b")));

        SearchServiceResponse<NpmPackageSearchResponse> response = service.query("pkg", 1, 2);

        assertThat(response.items()).hasSize(2);
        assertThat(response.totalResults()).isEqualTo(4321);
    }

    @Test
    void noMatchesGivesEmptyItemsAndZeroTotal() throws Exception {
        stubResponse(total(0));

        SearchServiceResponse<NpmPackageSearchResponse> response = service.query("nothing", 1, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalResults()).isZero();
    }

    @Test
    void skipsHitsWithoutSource() throws Exception {
        stubResponse(total(2), hit("kept", document("kept")), hit("dropped", null));

        SearchServiceResponse<NpmPackageSearchResponse> response = service.query("pkg", 1, 20);

        assertThat(response.items()).extracting(SearchServiceResponseItem::id).containsExactly("kept");
    }

    @Test
    void totalResultsFallsBackToItemCountWhenElasticsearchOmitsTheTotal() throws Exception {
        stubResponse(null, hit("a", document("a")), hit("b", document("b")));

        SearchServiceResponse<NpmPackageSearchResponse> response = service.query("pkg", 1, 20);

        assertThat(response.totalResults()).isEqualTo(2);
    }

    // ---- failures ----

    @Test
    void ioFailureIsRethrownAsRuntimeExceptionWithTheCause() throws Exception {
        IOException cause = new IOException("connection refused");
        when(client.search(any(SearchRequest.class), eq(NpmPackageDocument.class))).thenThrow(cause);

        assertThatThrownBy(() -> service.query("express", 1, 20))
                .isInstanceOf(RuntimeException.class)
                .hasCause(cause);
    }

    @Test
    void elasticsearchErrorIsRethrownAsRuntimeExceptionWithTheCause() throws Exception {
        ElasticsearchException cause = new ElasticsearchException("search", ErrorResponse.of(e -> e
                .status(404).error(err -> err.type("index_not_found_exception").reason("no such index"))));
        when(client.search(any(SearchRequest.class), eq(NpmPackageDocument.class))).thenThrow(cause);

        assertThatThrownBy(() -> service.query("express", 1, 20))
                .isInstanceOf(RuntimeException.class)
                .hasCause(cause);
    }

    // ---- helpers ----

    private SearchRequest runAndCaptureRequest(String query, int page, int pageSize) throws Exception {
        stubResponse(total(0));
        service.query(query, page, pageSize);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(captor.capture(), eq(NpmPackageDocument.class));
        return captor.getValue();
    }

    @SafeVarargs
    private void stubResponse(TotalHits total, Hit<NpmPackageDocument>... hits) throws IOException {
        HitsMetadata.Builder<NpmPackageDocument> metadata = new HitsMetadata.Builder<NpmPackageDocument>()
                .hits(new ArrayList<>(List.of(hits)));
        if (total != null) {
            metadata.total(total);
        }
        SearchResponse<NpmPackageDocument> response = new SearchResponse.Builder<NpmPackageDocument>()
                .took(1)
                .timedOut(false)
                .shards(ShardStatistics.of(s -> s.total(1).successful(1).failed(0)))
                .hits(metadata.build())
                .build();
        when(client.search(any(SearchRequest.class), eq(NpmPackageDocument.class))).thenReturn(response);
    }

    private static TotalHits total(long value) {
        return TotalHits.of(t -> t.value(value).relation(TotalHitsRelation.Eq));
    }

    private static Hit<NpmPackageDocument> hit(String id, NpmPackageDocument source) {
        return new Hit.Builder<NpmPackageDocument>().index(INDEX).id(id).source(source).build();
    }

    private static NpmPackageDocument document(String name) {
        return new NpmPackageDocument(name, name + " description", List.of("web", "framework"), "5.0.0", "MIT", TS, TS, TS);
    }
}
