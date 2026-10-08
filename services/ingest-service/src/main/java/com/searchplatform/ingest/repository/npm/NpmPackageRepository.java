package com.searchplatform.ingest.repository.npm;

import com.searchplatform.ingest.domain.npm.NpmPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface NpmPackageRepository extends JpaRepository<NpmPackage, String>, NpmPackageCustomRepository {

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE npm_package SET deleted = TRUE WHERE package_id IN :packageIds
            """, nativeQuery = true)
    public int markDeleted(List<String> packageIds);

}
