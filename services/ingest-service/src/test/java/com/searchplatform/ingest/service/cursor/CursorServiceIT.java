package com.searchplatform.ingest.service.cursor;

import com.searchplatform.ingest.domain.cursor.Cursor;
import com.searchplatform.ingest.repository.cursor.CursorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// NOT_SUPPORTED: no test-wide transaction, like production. Inside one, the persistence context would serve a stale
// entity after the native upsert and hide the very behaviour these tests check.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(CursorService.class)
@Testcontainers
class CursorServiceIT {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String SOURCE = "NpmPackageIngest";

    @Autowired
    CursorService service;

    @Autowired
    CursorRepository repository;

    @BeforeEach
    void emptyTable() {
        repository.deleteAll();
    }

    @Test
    void getCursorForSource_isEmptyWhenNothingIsStored() {
        assertThat(service.getCursorForSource(SOURCE)).isEmpty();
    }

    @Test
    void updateOrCreateCursor_createsTheRowWhenMissing() {
        service.updateOrCreateCursor(SOURCE, "100");

        Optional<Cursor> stored = service.getCursorForSource(SOURCE);
        assertThat(stored).isPresent();
        assertThat(stored.get().getCursorValue()).isEqualTo("100");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void updateOrCreateCursor_updatesTheExistingRowWithoutAddingAnother() {
        service.updateOrCreateCursor(SOURCE, "100");

        service.updateOrCreateCursor(SOURCE, "200");

        assertThat(service.getCursorForSource(SOURCE)).get()
                .extracting(Cursor::getCursorValue).isEqualTo("200");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void updateOrCreateCursor_keepsSourcesIndependent() {
        service.updateOrCreateCursor(SOURCE, "100");

        service.updateOrCreateCursor("OtherSource", "999");

        assertThat(service.getCursorForSource(SOURCE)).get()
                .extracting(Cursor::getCursorValue).isEqualTo("100");
        assertThat(service.getCursorForSource("OtherSource")).get()
                .extracting(Cursor::getCursorValue).isEqualTo("999");
    }
}
