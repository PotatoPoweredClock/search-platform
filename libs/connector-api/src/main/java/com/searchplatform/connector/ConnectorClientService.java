package com.searchplatform.connector;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

public class ConnectorClientService {
    private static final String HEADER_ACCEPT = "Accept";
    private static final String APPLICATION_JSON = "application/json";
    private static final String SHORT_PACKAGE_METADATA = "application/vnd.npm.install-v1+json"; // short metadata for package if I want to use later
    private static final long CONNECT_TIMEOUT_SECONDS = 5;
    private static final long REQUEST_TIMEOUT_SECONDS = 5;

    private final RestClient restClient;

    public ConnectorClientService() {
        this(newRestClient(newHttpClient()));
    }

    public ConnectorClientService(RestClient restClient) {
        this.restClient = restClient;
    }

    public <T> T makeRequest(URI uri, Class<T> resultClass) {
        return restClient.get().uri(uri).header(HEADER_ACCEPT, APPLICATION_JSON).retrieve().body(resultClass);
    }

    //default http client and rest client if none is provided
    private static HttpClient newHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS)).build();
    }

    private static RestClient newRestClient(HttpClient httpClient) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS));
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
