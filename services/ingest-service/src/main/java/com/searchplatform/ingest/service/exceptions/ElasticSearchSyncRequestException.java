package com.searchplatform.ingest.service.exceptions;

public class ElasticSearchSyncRequestException extends RuntimeException {
    public ElasticSearchSyncRequestException(Throwable cause) {
        super(cause);
    }

    public ElasticSearchSyncRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public ElasticSearchSyncRequestException(String message) {
        super(message);
    }
}
