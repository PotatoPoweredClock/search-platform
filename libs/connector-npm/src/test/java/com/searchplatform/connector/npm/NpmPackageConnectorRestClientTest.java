package com.searchplatform.connector.npm;

import com.searchplatform.connector.ConnectorClientService;
import com.searchplatform.connector.npm.response.NpmPackageFullResponse;
import com.searchplatform.model.connector.ConnectorCursor;
import com.searchplatform.model.event.change.ChangeEventPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Uses a real RestClient (and its real message converters) behind MockRestServiceServer
 * so we see the exceptions Spring actually throws, rather than ones we construct by hand.
 */
public class NpmPackageConnectorRestClientTest {
    private static final String CHANGES_URL = "https://replicate.npmjs.com/_changes?since=1&limit=4";
    private static final String PACKAGE1_URL = "https://registry.npmjs.org/package1";
    private static final String PACKAGE2_URL = "https://registry.npmjs.org/package2";

    private static final String PAGE_JSON = "{\"results\":[" +
            "{\"seq\":133509741,\"id\":\"package1\",\"changes\":[{\"rev\":\"10-82faa5e9b91cfa63b5bd967cc3a3b2fb\"}]}," +
            "{\"seq\":133509743,\"id\":\"package2\",\"changes\":[{\"rev\":\"53-dfe6f5070c97bbaba0a05f05d8bd7009\"}]}" +
            "],\"last_seq\":133509749}";

    // unpublished packages carry an object under time.unpublished and have no dist-tags
    private static final String UNPUBLISHED_JSON = "{\"_id\":\"package1\",\"name\":\"package1\"," +
            "\"time\":{\"created\":\"2026-08-01T19:20:42.380Z\"," +
            "\"unpublished\":{\"time\":\"2026-09-01T00:00:00.000Z\",\"versions\":[\"1.0.0\"]}}}";

    // republished package that still has an unpublished key: dist-tags mean it is live
    private static final String STALE_UNPUBLISHED_JSON = "{\"_id\":\"package1\",\"name\":\"package1Name\"," +
            "\"dist-tags\":{\"latest\":\"2.0.0\"}," +
            "\"time\":{\"created\":\"2026-08-01T19:20:42.380Z\",\"modified\":\"2026-09-30T01:56:34.655Z\"," +
            "\"2.0.0\":\"2026-09-30T01:56:34.318Z\"," +
            "\"unpublished\":{\"time\":\"2026-09-01T00:00:00.000Z\",\"versions\":[\"1.0.0\"]}}}";

    private static final String PACKAGE2_JSON = "{\"_id\":\"package2\",\"name\":\"package2Name\"," +
            "\"dist-tags\":{\"latest\":\"1.0.0\"},\"license\":\"MIT\",\"readme\":\"ignored\"," +
            "\"time\":{\"created\":\"2026-08-01T19:20:42.380Z\",\"modified\":\"2026-09-30T01:56:34.655Z\"," +
            "\"1.0.0\":\"2026-09-30T01:56:34.318Z\"}}";

    private MockRestServiceServer server;
    private ConnectorClientService service;
    private NpmPackageConnector connector;

    @BeforeEach
    public void before() {
        RestClient.Builder builder = RestClient.builder();
        // package requests can happen in any order
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        service = new ConnectorClientService(builder.build());
        connector = new NpmPackageConnector(service);
    }

    @Test
    public void unpublishedJsonMapsTheUnpublishedTime() {
        server.expect(requestTo(PACKAGE1_URL))
                .andRespond(withSuccess(UNPUBLISHED_JSON, MediaType.APPLICATION_JSON));

        NpmPackageFullResponse response = service.makeRequest(URI.create(PACKAGE1_URL), NpmPackageFullResponse.class);

        assertThat(response.getTime().getCreated()).isEqualTo("2026-08-01T19:20:42.380Z");
        assertThat(response.getTime().getUnpublished().getTime()).isEqualTo("2026-09-01T00:00:00.000Z");
        assertThat(response.getTime().getUnpublished().getVersions()).containsExactly("1.0.0");
        assertThat(response.getDistTags()).isNull();
    }

    @Test
    public void unpublishedPackageIsReportedDeletedAndOthersAreKept() {
        server.expect(method(GET)).andExpect(requestTo(CHANGES_URL))
                .andRespond(withSuccess(PAGE_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(PACKAGE1_URL))
                .andRespond(withSuccess(UNPUBLISHED_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(PACKAGE2_URL))
                .andRespond(withSuccess(PACKAGE2_JSON, MediaType.APPLICATION_JSON));

        ChangeEventPage results = connector.getChangePage(new ConnectorCursor("1"), 4);

        server.verify();
        assertThat(results.cursor().cursorValue()).isEqualTo("133509749");
        Map<String, NpmPackageEventChangeContent> eventsById = eventsById(results);
        assertThat(eventsById).containsOnlyKeys("package1", "package2");

        NpmPackageEventChangeContent unpublished = eventsById.get("package1");
        assertThat(unpublished.isDeleted()).isTrue();
        assertThat(unpublished.getName()).isNull();

        NpmPackageEventChangeContent live = eventsById.get("package2");
        assertThat(live.isDeleted()).isFalse();
        assertThat(live.getName()).isEqualTo("package2Name");
        assertThat(live.getLatestVersion()).isEqualTo("1.0.0");
    }

    @Test
    public void republishedPackageWithStaleUnpublishedKeyStaysLive() {
        server.expect(method(GET)).andExpect(requestTo(CHANGES_URL))
                .andRespond(withSuccess(PAGE_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(PACKAGE1_URL))
                .andRespond(withSuccess(STALE_UNPUBLISHED_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(PACKAGE2_URL))
                .andRespond(withSuccess(PACKAGE2_JSON, MediaType.APPLICATION_JSON));

        ChangeEventPage results = connector.getChangePage(new ConnectorCursor("1"), 4);

        NpmPackageEventChangeContent republished = eventsById(results).get("package1");
        assertThat(republished.isDeleted()).isFalse();
        assertThat(republished.getName()).isEqualTo("package1Name");
        assertThat(republished.getLatestVersion()).isEqualTo("2.0.0");
    }

    // events carry no guaranteed order, so tests look them up by id
    private static Map<String, NpmPackageEventChangeContent> eventsById(ChangeEventPage page) {
        return page.events().stream()
                .map(event -> (NpmPackageEventChangeContent) event.getContent())
                .collect(Collectors.toMap(NpmPackageEventChangeContent::getId, Function.identity()));
    }

    @Test
    public void serverErrorOnPackageIsRethrown() {
        server.expect(requestTo(CHANGES_URL))
                .andRespond(withSuccess(PAGE_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(PACKAGE1_URL))
                .andRespond(withServerError());
        // package2 may be requested before package1 throws
        server.expect(requestTo(PACKAGE2_URL))
                .andRespond(withSuccess(PACKAGE2_JSON, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> connector.getChangePage(new ConnectorCursor("1"), 4))
                .isInstanceOf(HttpServerErrorException.class);
    }
}
