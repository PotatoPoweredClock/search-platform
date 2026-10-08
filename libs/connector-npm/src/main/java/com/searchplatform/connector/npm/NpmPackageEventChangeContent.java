package com.searchplatform.connector.npm;

import com.searchplatform.model.event.change.ChangeSourceContent;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class NpmPackageEventChangeContent implements ChangeSourceContent {
    private boolean deleted = false;
    private String id;
    private String name;
    private String latestVersion;
    private String description;
    private List<String> keywords;
    private String license;
    private OffsetDateTime dateCreated;
    private OffsetDateTime dateModified;
    private OffsetDateTime dateLatestVersionModified;
}
