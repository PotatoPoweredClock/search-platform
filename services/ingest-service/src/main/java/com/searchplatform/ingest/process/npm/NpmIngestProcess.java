package com.searchplatform.ingest.process.npm;

import com.searchplatform.connector.Connector;
import com.searchplatform.connector.npm.NpmPackageEventChangeContent;
import com.searchplatform.ingest.domain.cursor.Cursor;
import com.searchplatform.ingest.domain.npm.NpmPackage;
import com.searchplatform.ingest.service.cursor.CursorService;
import com.searchplatform.ingest.service.npm.NpmElasticSearchSyncService;
import com.searchplatform.ingest.service.npm.NpmPackageService;
import com.searchplatform.model.connector.ConnectorCursor;
import com.searchplatform.model.event.change.ChangeEvent;
import com.searchplatform.model.event.change.ChangeEventPage;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@Component
public class NpmIngestProcess {
    private static final long DEFAULT_DELAY_MILS = 1000;
    private static final int MAX_DELAY_MULTIPLIER = 120;
    private static final int DEFAULT_LIMIT = 10;
    private static final String DEFAULT_CURSOR_VALUE = "133509727";
    private static final String CURSOR_SOURCE = "NpmPackageIngest";
    private static final Logger LOG = LoggerFactory.getLogger(NpmIngestProcess.class);
    private static final OffsetDateTime DEFAULT_TIME_FOR_PACKAGE_MAPPING = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC"));

    private final TaskScheduler scheduler;
    private final Connector connector;
    private final CursorService cursorService;
    private final NpmPackageService npmPackageService;
    private final NpmElasticSearchSyncService npmElasticSearchSyncService;

    private volatile boolean running;
    private volatile ScheduledFuture<?> next;
    private int delayMultiplier = 1;

    public NpmIngestProcess(
            @Qualifier("npmPackageConnector") Connector npmPackageConnector,
            @Qualifier("npmIngestScheduler") TaskScheduler npmIngestScheduler,
            CursorService cursorService,
            NpmPackageService npmPackageService,
            NpmElasticSearchSyncService npmElasticSearchSyncService) {
        this.connector = npmPackageConnector;
        this.scheduler = npmIngestScheduler;
        this.cursorService = cursorService;
        this.npmPackageService = npmPackageService;
        this.npmElasticSearchSyncService = npmElasticSearchSyncService;
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        running = true;
        schedule(Duration.ofMillis(DEFAULT_DELAY_MILS));
    }

    @PreDestroy
    void stop() {
        running = false;
        if (next != null) next.cancel(false);
    }

    private void schedule(Duration delay) {
        if (!running) return;
        next = scheduler.schedule(this::processPage, Instant.now().plus(delay));
    }

    private void processPage() {
        if (!running) return;

        try {
            ConnectorCursor npmCursor = getCursor();

            ChangeEventPage changes = connector.getChangePage(npmCursor, DEFAULT_LIMIT);

            //Separate events into those we want to update vs those we want to mark as deleted
            List<NpmPackage> packagesToUpdate = new ArrayList<>();
            List<String> packageIdsToMarkDelete = new ArrayList<>();

            for (ChangeEvent changeEvent : changes.events()) {
                NpmPackageEventChangeContent content = (NpmPackageEventChangeContent) changeEvent.getContent();
                if (content.isDeleted()) {
                    packageIdsToMarkDelete.add(content.getId());
                } else {
                    packagesToUpdate.add(mapToPackageDomainObject(content));
                }
            }

            //update database
            List<NpmPackage> updatedPackages = npmPackageService.syncUpdatedDeletedPackagesFromIngest(packagesToUpdate);
            npmPackageService.syncDeletedPackagesFromIngest(packageIdsToMarkDelete);

            //sync with ElasticSearch
            npmElasticSearchSyncService.updateNpmPackages(updatedPackages);
            npmElasticSearchSyncService.removeNpmPackages(packageIdsToMarkDelete);

            // update cursor in table
            updateCursor(changes.cursor().cursorValue());
            delayMultiplier = 1; //If we have a success, reset the delay multiplier to 1
            LOG.info("SUCCESS SYNCING PACKAGES");
        } catch (Exception e) { //TODO: expand for specific exceptions and better handling
            increaseDelayMultiplier();
            LOG.error("Ingest process failed. waiting for {} ms before retrying", DEFAULT_DELAY_MILS * delayMultiplier, e);
        }
        this.schedule(Duration.ofMillis(DEFAULT_DELAY_MILS * delayMultiplier));
    }

    private void increaseDelayMultiplier() {
        delayMultiplier = Math.min(delayMultiplier * 2, MAX_DELAY_MULTIPLIER);
    }

    private NpmPackage mapToPackageDomainObject(NpmPackageEventChangeContent toMap) {
        if (toMap == null) return null;
        //TODO: Optional log if an event is not a delete and does not have created or modified dates
        //move to mapstruct later?
        NpmPackage mapped = new NpmPackage();
        mapped.setPackageId(toMap.getId());
        mapped.setDeleted(toMap.isDeleted());
        mapped.setName(toMap.getName());
        mapped.setDescription(toMap.getDescription());
        mapped.setLatestVersion(toMap.getLatestVersion());
        mapped.setKeywords(toMap.getKeywords());
        mapped.setLicense(toMap.getLicense());
        mapped.setDatePackageCreated(toMap.getDateCreated() != null ? toMap.getDateCreated() : DEFAULT_TIME_FOR_PACKAGE_MAPPING);
        mapped.setDatePackageModified(toMap.getDateModified() != null ? toMap.getDateModified() : DEFAULT_TIME_FOR_PACKAGE_MAPPING);
        mapped.setDateLatestVersionModified(toMap.getDateLatestVersionModified());
        return mapped;
    }

    private ConnectorCursor getCursor() {
        Cursor dbCursor = cursorService.getCursorForSource(CURSOR_SOURCE);
        if (dbCursor == null) {
            return new ConnectorCursor(DEFAULT_CURSOR_VALUE);
        }
        return new ConnectorCursor(dbCursor.getCursorValue());
    }

    private void updateCursor(String newCursorValue) {
        if (newCursorValue == null || newCursorValue.isBlank()) return;
        cursorService.updateOrCreateCursor(CURSOR_SOURCE, newCursorValue);
    }
}
