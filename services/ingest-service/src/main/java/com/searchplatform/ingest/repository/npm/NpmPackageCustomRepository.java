package com.searchplatform.ingest.repository.npm;

import com.searchplatform.ingest.domain.npm.NpmPackage;

import java.util.List;

public interface NpmPackageCustomRepository {
    void upsertAll(List<NpmPackage> packages);
}
