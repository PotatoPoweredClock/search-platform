package com.searchplatform.search.service.npm;

import java.time.OffsetDateTime;
import java.util.List;

public record NpmPackageSearchResponse(
        String name,
        String description,
        List<String> keywords,
        String latestVersion,
        String license,
        OffsetDateTime datePackageCreated,
        OffsetDateTime datePackageModified,
        OffsetDateTime dateLatestVersionModified) {
}
