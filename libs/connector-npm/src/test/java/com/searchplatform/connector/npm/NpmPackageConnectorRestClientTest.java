package com.searchplatform.connector.npm;

import com.searchplatform.connector.npm.response.NpmPackageFullResponse;
import com.searchplatform.model.connector.Cursor;
import com.searchplatform.model.event.change.ChangeEventPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

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

    // unpublished packages carry an object under time.unpublished, which cannot map to Map<String,String>
    private static final String UNPUBLISHED_JSON = "{\"_id\":\"package1\",\"name\":\"package1\"," +
            "\"time\":{\"created\":\"2026-08-01T19:20:42.380Z\"," +
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
        server = MockRestServiceServer.bindTo(builder).build();
        service = new ConnectorClientService(builder.build());
        connector = new NpmPackageConnector(service);
    }

    @Test
    public void mappingFailureSurfacesAsRestClientExceptionWithNotReadableCause() {
        server.expect(requestTo(PACKAGE1_URL))
                .andRespond(withSuccess(UNPUBLISHED_JSON, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.makeRequest(URI.create(PACKAGE1_URL), NpmPackageFullResponse.class))
                .isInstanceOf(RestClientException.class)
                .hasCauseInstanceOf(HttpMessageNotReadableException.class);
    }

    @Test
    public void unpublishedPackageIsSkippedAndOthersAreKept() {
        server.expect(method(GET)).andExpect(requestTo(CHANGES_URL))
                .andRespond(withSuccess(PAGE_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(PACKAGE1_URL))
                .andRespond(withSuccess(UNPUBLISHED_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(PACKAGE2_URL))
                .andRespond(withSuccess(PACKAGE2_JSON, MediaType.APPLICATION_JSON));

        ChangeEventPage results = connector.getChangePage(new Cursor("1"), 4);

        server.verify();
        assertThat(results.cursor().cursorValue()).isEqualTo("133509749");
        assertThat(results.events()).hasSize(1);
        PackageEventChangeContent only = (PackageEventChangeContent) results.events().get(0).getContent();
        assertThat(only.getId()).isEqualTo("package2");
        assertThat(only.getName()).isEqualTo("package2Name");
        assertThat(only.getLatestVersion()).isEqualTo("1.0.0");
    }

    @Test
    public void serverErrorOnPackageIsRethrown() {
        server.expect(requestTo(CHANGES_URL))
                .andRespond(withSuccess(PAGE_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(PACKAGE1_URL))
                .andRespond(withServerError());

        assertThatThrownBy(() -> connector.getChangePage(new Cursor("1"), 4))
                .isInstanceOf(HttpServerErrorException.class);
    }
}
