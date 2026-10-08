package com.searchplatform.model.search;

import java.time.OffsetDateTime;
import java.util.List;

/*
  The shape of one document in the packages index. Field names must match
  elasticsearch/npm-packages-index.json exactly: the mapping is dynamic: strict.
  The package name is used as the document _id, so it is not a separate field here.
 */
public record NpmPackageDocument(
        String name,
        String description,
        List<String> keywords,
        String latestVersion,
        String license,
        OffsetDateTime datePackageCreated,
        OffsetDateTime datePackageModified,
        OffsetDateTime dateLatestVersionModified) {
}
