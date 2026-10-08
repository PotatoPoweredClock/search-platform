package com.searchplatform.ingest.repository.npm;

import com.searchplatform.ingest.domain.npm.NpmPackage;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class NpmPackageRepositoryIT {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    // Same driver mode as production (application.yml), so batches are rewritten into multi-row inserts.
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "&reWriteBatchedInserts=true");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime EPOCH = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);

    @Autowired
    NpmPackageRepository repository;

    @Autowired
    EntityManager entityManager;

    // ---- upsertAll: inserts and updates ----

    @Test
    void upsertAll_insertsNewPackages() {
        repository.upsertAll(List.of(
                pkg("left-pad", "1.0.0", T0, List.of("string", "pad")),
                pkg("lodash", "4.17.21", T0, null)));

        List<NpmPackage> saved = readAll();

        assertThat(saved).extracting(NpmPackage::getPackageId).containsExactlyInAnyOrder("left-pad", "lodash");
        NpmPackage leftPad = byPackageId(saved, "left-pad");
        assertThat(leftPad.isDeleted()).isFalse();
        assertThat(leftPad.getKeywords()).containsExactly("string", "pad");
        assertThat(leftPad.getDatePackageModified().toInstant()).isEqualTo(T0.toInstant());
        assertThat(byPackageId(saved, "lodash").getKeywords()).isNull();
    }

    @Test
    void upsertAll_updatesExistingPackageWhenModifiedDateIsNewer() {
        repository.upsertAll(List.of(pkg("left-pad", "1.0.0", T0, List.of("pad"))));

        repository.upsertAll(List.of(pkg("left-pad", "1.1.0", T0.plusDays(1), List.of("pad", "string"))));

        List<NpmPackage> saved = readAll();
        assertThat(saved).hasSize(1);
        NpmPackage updated = saved.getFirst();
        assertThat(updated.getLatestVersion()).isEqualTo("1.1.0");
        assertThat(updated.getKeywords()).containsExactly("pad", "string");
        assertThat(updated.getDatePackageModified().toInstant()).isEqualTo(T0.plusDays(1).toInstant());
    }

    @Test
    void upsertAll_mixesInsertsAndUpdatesInOneBatch() {
        repository.upsertAll(List.of(pkg("a", "1.0.0", T0, null), pkg("b", "1.0.0", T0, null)));

        repository.upsertAll(List.of(
                pkg("b", "2.0.0", T0.plusDays(1), null),
                pkg("c", "1.0.0", T0, null)));

        List<NpmPackage> saved = readAll();
        assertThat(saved).extracting(NpmPackage::getPackageId).containsExactlyInAnyOrder("a", "b", "c");
        assertThat(byPackageId(saved, "a").getLatestVersion()).isEqualTo("1.0.0");
        assertThat(byPackageId(saved, "b").getLatestVersion()).isEqualTo("2.0.0");
    }

    // ---- upsertAll: events that must not change a row ----

    @Test
    void upsertAll_ignoresEventOlderThanStoredModifiedDate() {
        repository.upsertAll(List.of(pkg("left-pad", "2.0.0", T0.plusDays(2), null)));

        repository.upsertAll(List.of(pkg("left-pad", "1.0.0", T0.plusDays(1), null)));

        NpmPackage stored = readAll().getFirst();
        assertThat(stored.getLatestVersion()).isEqualTo("2.0.0");
        assertThat(stored.getDatePackageModified().toInstant()).isEqualTo(T0.plusDays(2).toInstant());
    }

    @Test
    void upsertAll_ignoresEventWithTheSameModifiedDate() {
        repository.upsertAll(List.of(pkg("left-pad", "1.0.0", T0, null)));

        repository.upsertAll(List.of(pkg("left-pad", "9.9.9", T0, null)));

        assertThat(readAll().getFirst().getLatestVersion()).isEqualTo("1.0.0");
    }

    @Test
    void upsertAll_skipsStaleRowButStillInsertsOthersInTheSameBatch() {
        repository.upsertAll(List.of(pkg("a", "2.0.0", T0.plusDays(2), null)));

        repository.upsertAll(List.of(
                pkg("a", "1.0.0", T0, null),
                pkg("b", "1.0.0", T0, null)));

        List<NpmPackage> saved = readAll();
        assertThat(saved).extracting(NpmPackage::getPackageId).containsExactlyInAnyOrder("a", "b");
        assertThat(byPackageId(saved, "a").getLatestVersion()).isEqualTo("2.0.0");
    }

    @Test
    void upsertAll_doesNotUpdateAPackageThatIsMarkedDeleted() {
        repository.upsertAll(List.of(pkg("left-pad", "1.0.0", T0, null)));
        repository.markDeleted(List.of("left-pad"));

        repository.upsertAll(List.of(pkg("left-pad", "2.0.0", T0.plusDays(1), null)));

        NpmPackage stored = readAll().getFirst();
        assertThat(stored.isDeleted()).isTrue();
        assertThat(stored.getLatestVersion()).isEqualTo("1.0.0");
    }

    @Test
    void upsertAll_neverMarksAnExistingPackageDeleted() {
        repository.upsertAll(List.of(pkg("left-pad", "1.0.0", T0, null)));

        NpmPackage deleteEvent = pkg("left-pad", "2.0.0", T0.plusDays(1), null);
        deleteEvent.setDeleted(true);
        repository.upsertAll(List.of(deleteEvent));

        NpmPackage stored = readAll().getFirst();
        assertThat(stored.isDeleted()).isFalse();
        assertThat(stored.getLatestVersion()).isEqualTo("1.0.0");
    }

    // ---- upsertAll: placeholder dates for packages missing them ----

    @Test
    void upsertAll_placeholderDateDoesNotOverwriteARealDate() {
        repository.upsertAll(List.of(pkg("left-pad", "2.0.0", T0.plusDays(5), List.of("real"))));

        repository.upsertAll(List.of(pkg("left-pad", "degraded", EPOCH, null)));

        NpmPackage stored = readAll().getFirst();
        assertThat(stored.getLatestVersion()).isEqualTo("2.0.0");
        assertThat(stored.getKeywords()).containsExactly("real");
        assertThat(stored.getDatePackageModified().toInstant()).isEqualTo(T0.plusDays(5).toInstant());
    }

    @Test
    void upsertAll_realEventReplacesARowStoredWithAPlaceholderDate() {
        repository.upsertAll(List.of(pkg("left-pad", "degraded", EPOCH, null)));

        repository.upsertAll(List.of(pkg("left-pad", "1.0.0", T0, List.of("real"))));

        NpmPackage stored = readAll().getFirst();
        assertThat(stored.getLatestVersion()).isEqualTo("1.0.0");
        assertThat(stored.getDatePackageModified().toInstant()).isEqualTo(T0.toInstant());
    }

    // ---- upsertAll: input edge cases ----

    @Test
    void upsertAll_withEmptyListDoesNothing() {
        repository.upsertAll(List.of());

        assertThat(readAll()).isEmpty();
    }

    @Test
    void upsertAll_withNullListDoesNothing() {
        repository.upsertAll(null);

        assertThat(readAll()).isEmpty();
    }

    @Test
    void upsertAll_handlesALargeBatch() {
        List<NpmPackage> batch = IntStream.range(0, 2_000)
                .mapToObj(i -> pkg("pkg-" + i, "1.0.0", T0, List.of("k" + i)))
                .toList();

        repository.upsertAll(batch);

        assertThat(repository.count()).isEqualTo(2_000);
    }

    // ---- markDeleted ----

    @Test
    void markDeleted_marksOnlyMatchingRowsAndKeepsTheirData() {
        repository.upsertAll(List.of(
                pkg("a", "1.0.0", T0, List.of("keep")),
                pkg("b", "1.0.0", T0, null),
                pkg("c", "1.0.0", T0, null)));

        int marked = repository.markDeleted(List.of("a", "c"));

        assertThat(marked).isEqualTo(2);
        List<NpmPackage> saved = readAll();
        assertThat(saved).hasSize(3);
        assertThat(byPackageId(saved, "a").isDeleted()).isTrue();
        assertThat(byPackageId(saved, "b").isDeleted()).isFalse();
        assertThat(byPackageId(saved, "c").isDeleted()).isTrue();
        NpmPackage a = byPackageId(saved, "a");
        assertThat(a.getName()).isEqualTo("a");
        assertThat(a.getKeywords()).containsExactly("keep");
        assertThat(a.getDatePackageModified().toInstant()).isEqualTo(T0.toInstant());
    }

    @Test
    void markDeleted_withUnknownIdChangesNothing() {
        repository.upsertAll(List.of(pkg("a", "1.0.0", T0, null)));

        int marked = repository.markDeleted(List.of("never-seen"));

        assertThat(marked).isZero();
        List<NpmPackage> saved = readAll();
        assertThat(saved).extracting(NpmPackage::getPackageId).containsExactly("a");
        assertThat(saved.getFirst().isDeleted()).isFalse();
    }

    @Test
    void markDeleted_isIdempotent() {
        repository.upsertAll(List.of(pkg("a", "1.0.0", T0, null)));

        repository.markDeleted(List.of("a"));
        repository.markDeleted(List.of("a"));

        assertThat(readAll().getFirst().isDeleted()).isTrue();
    }

    // JDBC writes bypass the persistence context, so drop cached entities before reading back.
    private List<NpmPackage> readAll() {
        entityManager.clear();
        return repository.findAll();
    }

    private static NpmPackage byPackageId(List<NpmPackage> packages, String packageId) {
        return packages.stream().filter(p -> p.getPackageId().equals(packageId)).findFirst().orElseThrow();
    }

    private static NpmPackage pkg(String packageId, String version, OffsetDateTime modified, List<String> keywords) {
        NpmPackage p = new NpmPackage();
        p.setPackageId(packageId);
        p.setName(packageId);
        p.setLatestVersion(version);
        p.setDescription("description of " + packageId);
        p.setKeywords(keywords);
        p.setLicense("MIT");
        p.setDatePackageCreated(T0);
        p.setDatePackageModified(modified);
        p.setDateLatestVersionModified(modified);
        return p;
    }
}
