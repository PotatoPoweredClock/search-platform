package com.searchplatform.connector.npm;

import com.searchplatform.connector.Connector;
import com.searchplatform.connector.ConnectorClientService;
import com.searchplatform.connector.npm.response.NpmPackageChangePageResponse;
import com.searchplatform.connector.npm.response.NpmPackageChangeResponse;
import com.searchplatform.connector.npm.response.NpmPackageFullResponse;
import com.searchplatform.model.connector.ConnectorCursor;
import com.searchplatform.model.event.change.ChangeEvent;
import com.searchplatform.model.event.change.ChangeEventPage;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NpmPackageConnector implements Connector {
    private static final String CHANGE_URL = "https://replicate.npmjs.com/_changes";
    private static final String REGISTRY_URL = "https://registry.npmjs.org";
    private static final String PARAM_SINCE = "since";
    private static final String PARAM_LIMIT = "limit";


    private static final Logger LOG = LoggerFactory.getLogger(NpmPackageConnector.class);

    private final ConnectorClientService connectorClientService;

    public NpmPackageConnector(ConnectorClientService connectorClientService) {
        this.connectorClientService = connectorClientService;
    }

    @Override
    public ChangeEventPage getChangePage(ConnectorCursor cursor, int limit) {
        if (cursor == null
                || cursor.cursorValue() == null
                || cursor.cursorValue().isEmpty()
                || limit < 1) {
            throw new IllegalArgumentException("Invalid cursor or limit provided.");
        }

        NpmPackageChangePageResponse response = connectorClientService.makeRequest(getPackageChangeSinceURI(cursor.cursorValue(), limit), NpmPackageChangePageResponse.class);

        List<ChangeEvent> packageChangeList = new ArrayList<>();
        Map<String, NpmPackageChangeResponse> dedupedPackages = new HashMap<>();

        //dedupe giving priority to deleted events to avoid making repeated calls
        if (response.getResults() != null) {
            for (NpmPackageChangeResponse packageChange : response.getResults()) {
                dedupedPackages.merge(packageChange.getId(), packageChange, (current, next) -> next.isDeleted() ? next : current);
            }
        }

        for (NpmPackageChangeResponse packageChange : dedupedPackages.values()) {
            NpmPackageEventChangeContent packageContent = getPackageInfo(packageChange.getId(), packageChange.isDeleted());
            if (packageContent != null) {
                ChangeEvent event = new ChangeEvent();
                event.setContent(packageContent);
                packageChangeList.add(event);
            }
        }
        return new ChangeEventPage(new ConnectorCursor(response.getLastSequence()), packageChangeList);
    }

    private boolean isUnpublished(NpmPackageFullResponse response) {
        boolean hasUnpublishedTime = response.getTime() != null && response.getTime().getUnpublished() != null;
        boolean hasDistTags = response.getDistTags() != null && !response.getDistTags().isEmpty();
        return hasUnpublishedTime && !hasDistTags;
    }

    private NpmPackageEventChangeContent getPackageInfo(String packageId, boolean deleted) {
        NpmPackageEventChangeContent result = new NpmPackageEventChangeContent();
        result.setId(packageId);

        //if the package event has the deleted field we want to return an event noting it was deleted
        if (deleted) {
            result.setDeleted(true);
            return result;
        }

        //make request to get package from npm then map to our internal response object
        NpmPackageFullResponse response;
        try {
            response = connectorClientService.makeRequest(getPackageMetadataURI(packageId), NpmPackageFullResponse.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                LOG.debug("package not found for id:{} treating as deleted", packageId);
                result.setDeleted(true);
                return result;
            }
            throw e;
        } catch (RestClientException e) {
            if (e.getCause() instanceof HttpMessageNotReadableException) {
                LOG.warn("Malformed package found for id{} skipping.", packageId, e);
                return null;
            } else {
                throw e;
            }
        }

        // An unpublished package still returns a stub document: time.unpublished is set and there are no dist-tags.
        // Requiring both keeps a republished package live even if a stale unpublished key were left behind.
        if (isUnpublished(response)) {
            result.setDeleted(true);
            return result;
        }

        //map our internal response object to the change data we will report.
        result.setName(response.getName());
        if (response.getDistTags() != null) result.setLatestVersion(response.getDistTags().get("latest"));
        result.setDescription(response.getDescription());
        result.setKeywords(response.getKeywords());
        result.setLicense(response.getLicense());
        if (response.getTime() != null) {
            result.setDateCreated(toDate(response.getTime().getCreated()));
            result.setDateModified(toDate(response.getTime().getModified()));
            if (response.getTime().getVersionTimes() != null) {
                result.setDateLatestVersionModified(toDate(response.getTime().getVersionTime(result.getLatestVersion())));
            }
        }
        return result;
    }

    /**
     * Converts a timestamp string into an {@code OffsetDateTime} object.
     * If the input string is null, empty, or cannot be parsed, this method returns {@code null}.
     *
     * @param timestamp the timestamp string to be converted; may be null or empty
     * @return the {@code OffsetDateTime} representation of the timestamp, or {@code null} if the input is null, empty, or invalid
     */
    private OffsetDateTime toDate(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(timestamp);
        } catch (Exception e) {
            return null;
        }
    }

    private URI getPackageChangeSinceURI(String since, int limit) {
        try {
            return new URIBuilder(CHANGE_URL)
                    .addParameter(PARAM_SINCE, since)
                    .addParameter(PARAM_LIMIT, String.valueOf(limit))
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private URI getPackageMetadataURI(String packageId) {
        try {
            return new URIBuilder(REGISTRY_URL)
                    .appendPath(packageId)
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
