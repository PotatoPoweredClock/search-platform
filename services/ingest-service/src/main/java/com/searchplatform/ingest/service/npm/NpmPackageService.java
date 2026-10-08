package com.searchplatform.ingest.service.npm;

import com.searchplatform.ingest.domain.npm.NpmPackage;
import com.searchplatform.ingest.repository.npm.NpmPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NpmPackageService {
    private final NpmPackageRepository repository;

    public List<NpmPackage> syncUpdatedDeletedPackagesFromIngest(List<NpmPackage> packages) {
        if (packages == null || packages.isEmpty()) return new ArrayList<>();

        repository.upsertAll(packages);

        return repository.findAllById(packages.stream().map(NpmPackage::getPackageId).toList());
    }

    public int syncDeletedPackagesFromIngest(List<String> packages) {
        if (packages == null || packages.isEmpty()) return 0;

        return repository.markDeleted(packages);
    }
}
