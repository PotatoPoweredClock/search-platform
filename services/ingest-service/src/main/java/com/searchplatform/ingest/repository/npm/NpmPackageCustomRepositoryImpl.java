package com.searchplatform.ingest.repository.npm;

import com.searchplatform.ingest.domain.npm.NpmPackage;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class NpmPackageCustomRepositoryImpl implements NpmPackageCustomRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String UPSERT_ALL_SQL = """
            INSERT INTO npm_package (
                deleted, package_id, name, latest_version, description, keywords, license,
                date_package_created, date_package_modified, date_latest_version_modified)
            VALUES (
                :deleted, :packageId, :name, :latestVersion, :description, :keywords, :license,
                :datePackageCreated, :datePackageModified, :dateLatestVersionModified)
            ON CONFLICT (package_id) DO UPDATE SET
                name                         = EXCLUDED.name,
                latest_version               = EXCLUDED.latest_version,
                description                  = EXCLUDED.description,
                keywords                     = EXCLUDED.keywords,
                license                      = EXCLUDED.license,
                date_package_created         = EXCLUDED.date_package_created,
                date_package_modified        = EXCLUDED.date_package_modified,
                date_latest_version_modified = EXCLUDED.date_latest_version_modified
            WHERE npm_package.deleted = FALSE AND EXCLUDED.deleted = FALSE
               AND EXCLUDED.date_package_modified > npm_package.date_package_modified 
            """;

    public NpmPackageCustomRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void upsertAll(List<NpmPackage> packages) {
        //reminder dupe records will cause it to fail we dedupe outside so we are fine for now
        if (packages == null || packages.isEmpty()) return;

        SqlParameterSource[] batch = packages.stream()
                .map(this::toSQLParams)
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(UPSERT_ALL_SQL, batch);
    }

    private SqlParameterSource toSQLParams(NpmPackage p) {
        return new MapSqlParameterSource()
                .addValue("deleted", p.isDeleted())
                .addValue("packageId", p.getPackageId())
                .addValue("name", p.getName())
                .addValue("latestVersion", p.getLatestVersion())
                .addValue("description", p.getDescription())
                .addValue("keywords", p.getKeywords() == null ? null : p.getKeywords().toArray(String[]::new))
                .addValue("license", p.getLicense())
                .addValue("datePackageCreated", p.getDatePackageCreated())
                .addValue("datePackageModified", p.getDatePackageModified())
                .addValue("dateLatestVersionModified", p.getDateLatestVersionModified());
    }
}
