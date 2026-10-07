package com.searchplatform.ingest.domain.npm;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "npm_package")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class NpmPackage {
    @Id
    @EqualsAndHashCode.Include
    private String packageId;
    private boolean deleted = false;
    private String name;
    private String latestVersion;
    private String description;
    private List<String> keywords;
    private String license;
    @Column(nullable = false)
    private OffsetDateTime datePackageCreated;
    @Column(nullable = false)
    private OffsetDateTime datePackageModified;
    private OffsetDateTime dateLatestVersionModified;
}
