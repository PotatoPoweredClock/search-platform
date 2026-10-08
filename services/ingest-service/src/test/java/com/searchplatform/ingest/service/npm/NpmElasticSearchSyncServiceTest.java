package com.searchplatform.ingest.service.npm;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;
import co.elastic.clients.util.ObjectBuilder;
import com.searchplatform.ingest.domain.npm.NpmPackage;
import com.searchplatform.ingest.service.exceptions.ElasticSearchSyncRequestException;
import com.searchplatform.model.search.NpmPackageDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NpmElasticSearchSyncServiceTest {
    private static final String INDEX = "test-index";

    @Mock
    private ElasticsearchClient client;

    @Captor
    private ArgumentCaptor<Function<BulkRequest.Builder, ObjectBuilder<BulkRequest>>> requestCaptor;

    private NpmElasticSearchSyncService service;

    @BeforeEach
    void setUp() {
        service = new NpmElasticSearchSyncService(client, INDEX);
    }

    // ---- updateNpmPackages ----

    @Test
    void updateDoesNothingForNullOrEmptyInput() {
        service.updateNpmPackages(null);
        service.updateNpmPackages(List.of());

        verifyNoInteractions(client);
    }

    @Test
    void updateSendsOneIndexOperationPerPackageToConfiguredIndex() throws IOException {
        when(client.bulk(anyBulkFunction())).thenReturn(response(item(OperationType.Index, "a", 201), item(OperationType.Index, "b", 200)));

        service.updateNpmPackages(List.of(pkg("a"), pkg("b")));

        BulkRequest request = capturedRequest();
        assertThat(request.index()).isEqualTo(INDEX);
        assertThat(request.operations()).hasSize(2);
        assertThat(request.operations()).allSatisfy(op -> assertThat(op.isIndex()).isTrue());
        assertThat(request.operations()).extracting(op -> op.index().id()).containsExactly("a", "b");
    }

    @Test
    void updateMapsPackageFieldsToDocument() throws IOException {
        when(client.bulk(anyBulkFunction())).thenReturn(response(item(OperationType.Index, "a", 201)));
        NpmPackage pkg = pkg("a");

        service.updateNpmPackages(List.of(pkg));

        Object document = capturedRequest().operations().getFirst().index().document();
        assertThat(document).isEqualTo(new NpmPackageDocument(
                pkg.getName(),
                pkg.getDescription(),
                pkg.getKeywords(),
                pkg.getLatestVersion(),
                pkg.getLicense(),
                pkg.getDatePackageCreated(),
                pkg.getDatePackageModified(),
                pkg.getDateLatestVersionModified()));
    }

    @Test
    void updateDoesNotThrowWhenEveryItemSucceeds() throws IOException {
        when(client.bulk(anyBulkFunction())).thenReturn(response(item(OperationType.Index, "a", 201), item(OperationType.Index, "b", 200)));

        assertThatCode(() -> service.updateNpmPackages(List.of(pkg("a"), pkg("b")))).doesNotThrowAnyException();
    }

    @Test
    void updateThrowsPartialErrorWhenAnyItemFails() throws IOException {
        when(client.bulk(anyBulkFunction())).thenReturn(response(
                item(OperationType.Index, "a", 201),
                failedItem(OperationType.Index, "b", 400, "mapper_parsing_exception", "bad field")));

        assertThatThrownBy(() -> service.updateNpmPackages(List.of(pkg("a"), pkg("b"))))
                .isInstanceOf(ElasticSearchSyncRequestException.class)
                .hasMessage("Partial error indexing packages")
                .hasNoCause();
    }

    @Test
    void updateTreatsRejectedExecutionAsFailure() throws IOException {
        when(client.bulk(anyBulkFunction())).thenReturn(response(
                failedItem(OperationType.Index, "a", 429, "es_rejected_execution_exception", "queue full")));

        assertThatThrownBy(() -> service.updateNpmPackages(List.of(pkg("a"))))
                .isInstanceOf(ElasticSearchSyncRequestException.class);
    }

    @Test
    void updateWrapsIOException() throws IOException {
        IOException cause = new IOException("connection refused");
        when(client.bulk(anyBulkFunction())).thenThrow(cause);

        assertThatThrownBy(() -> service.updateNpmPackages(List.of(pkg("a"))))
                .isInstanceOf(ElasticSearchSyncRequestException.class)
                .hasMessage("Error with sync request")
                .hasCause(cause);
    }

    @Test
    void updateWrapsElasticsearchException() throws IOException {
        ElasticsearchException cause = esException();
        when(client.bulk(anyBulkFunction())).thenThrow(cause);

        assertThatThrownBy(() -> service.updateNpmPackages(List.of(pkg("a"))))
                .isInstanceOf(ElasticSearchSyncRequestException.class)
                .hasMessage("Error with sync request")
                .hasCause(cause);
    }

    // ---- removeNpmPackages ----

    @Test
    void removeDoesNothingForNullOrEmptyInput() {
        service.removeNpmPackages(null);
        service.removeNpmPackages(List.of());

        verifyNoInteractions(client);
    }

    @Test
    void removeSendsOneDeleteOperationPerIdToConfiguredIndex() throws IOException {
        when(client.bulk(anyBulkFunction())).thenReturn(response(item(OperationType.Delete, "a", 200), item(OperationType.Delete, "b", 200)));

        service.removeNpmPackages(List.of("a", "b"));

        BulkRequest request = capturedRequest();
        assertThat(request.index()).isEqualTo(INDEX);
        assertThat(request.operations()).hasSize(2);
        assertThat(request.operations()).allSatisfy(op -> assertThat(op.isDelete()).isTrue());
        assertThat(request.operations()).extracting(op -> op.delete().id()).containsExactly("a", "b");
    }

    @Test
    void removeTreatsNotFoundAsSuccess() throws IOException {
        when(client.bulk(anyBulkFunction())).thenReturn(response(item(OperationType.Delete, "a", 200), item(OperationType.Delete, "gone", 404)));

        assertThatCode(() -> service.removeNpmPackages(List.of("a", "gone"))).doesNotThrowAnyException();
    }

    @Test
    void removeThrowsPartialErrorWhenAnyItemFails() throws IOException {
        when(client.bulk(anyBulkFunction())).thenReturn(response(
                item(OperationType.Delete, "a", 200),
                failedItem(OperationType.Delete, "b", 500, "internal_error", "boom")));

        assertThatThrownBy(() -> service.removeNpmPackages(List.of("a", "b")))
                .isInstanceOf(ElasticSearchSyncRequestException.class)
                .hasMessage("Partial error removing packages")
                .hasNoCause();
    }

    @Test
    void removeWrapsIOException() throws IOException {
        IOException cause = new IOException("timeout");
        when(client.bulk(anyBulkFunction())).thenThrow(cause);

        assertThatThrownBy(() -> service.removeNpmPackages(List.of("a")))
                .isInstanceOf(ElasticSearchSyncRequestException.class)
                .hasMessage("Error with sync request")
                .hasCause(cause);
    }

    @Test
    void removeWrapsElasticsearchException() throws IOException {
        ElasticsearchException cause = esException();
        when(client.bulk(anyBulkFunction())).thenThrow(cause);

        assertThatThrownBy(() -> service.removeNpmPackages(List.of("a")))
                .isInstanceOf(ElasticSearchSyncRequestException.class)
                .hasMessage("Error with sync request")
                .hasCause(cause);
    }

    // ---- helpers ----

    // bulk() is overloaded (BulkRequest vs builder lambda); pin the lambda overload the service uses.
    private static Function<BulkRequest.Builder, ObjectBuilder<BulkRequest>> anyBulkFunction() {
        return ArgumentMatchers.<Function<BulkRequest.Builder, ObjectBuilder<BulkRequest>>>any();
    }

    // Replays the lambda the service passed to bulk() so the built request can be asserted on.
    private BulkRequest capturedRequest() throws IOException {
        verify(client).bulk(requestCaptor.capture());
        return requestCaptor.getValue().apply(new BulkRequest.Builder()).build();
    }

    private static BulkResponse response(BulkResponseItem... items) {
        boolean errors = List.of(items).stream().anyMatch(i -> i.error() != null);
        return BulkResponse.of(b -> b.errors(errors).took(1).items(List.of(items)));
    }

    private static BulkResponseItem item(OperationType type, String id, int status) {
        return BulkResponseItem.of(i -> i.operationType(type).index(INDEX).id(id).status(status));
    }

    private static BulkResponseItem failedItem(OperationType type, String id, int status, String errorType, String reason) {
        return BulkResponseItem.of(i -> i.operationType(type).index(INDEX).id(id).status(status)
                .error(e -> e.type(errorType).reason(reason)));
    }

    private static ElasticsearchException esException() {
        return new ElasticsearchException("bulk", ErrorResponse.of(r -> r.status(503)
                .error(e -> e.type("unavailable_shards_exception").reason("no shards"))));
    }

    private static NpmPackage pkg(String id) {
        NpmPackage pkg = new NpmPackage();
        pkg.setPackageId(id);
        pkg.setName(id);
        pkg.setDescription("description of " + id);
        pkg.setKeywords(List.of("one", "two"));
        pkg.setLatestVersion("1.2.3");
        pkg.setLicense("MIT");
        pkg.setDatePackageCreated(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        pkg.setDatePackageModified(OffsetDateTime.parse("2026-02-01T00:00:00Z"));
        pkg.setDateLatestVersionModified(OffsetDateTime.parse("2026-03-01T00:00:00Z"));
        return pkg;
    }
}
