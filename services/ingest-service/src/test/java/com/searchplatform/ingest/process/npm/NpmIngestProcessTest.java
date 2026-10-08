package com.searchplatform.ingest.process.npm;

import com.searchplatform.connector.Connector;
import com.searchplatform.connector.npm.NpmPackageEventChangeContent;
import com.searchplatform.ingest.domain.cursor.Cursor;
import com.searchplatform.ingest.domain.npm.NpmPackage;
import com.searchplatform.ingest.service.cursor.CursorService;
import com.searchplatform.ingest.service.exceptions.ElasticSearchSyncRequestException;
import com.searchplatform.ingest.service.npm.NpmElasticSearchSyncService;
import com.searchplatform.ingest.service.npm.NpmPackageService;
import com.searchplatform.model.connector.ConnectorCursor;
import com.searchplatform.model.event.change.ChangeEvent;
import com.searchplatform.model.event.change.ChangeEventPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
  processPage() is private and re-schedules itself, so tests drive it like this:
    1. start() schedules the first run (package-private, so callable from this package).
    2. runScheduledTask() grabs the Runnable handed to the mocked TaskScheduler and runs it.
    3. Each run schedules the next one, so call runScheduledTask() again for the following page.
 */
@ExtendWith(MockitoExtension.class)
class NpmIngestProcessTest {
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final String CURSOR_SOURCE = "NpmPackageIngest";
    private static final String DEFAULT_CURSOR_VALUE = "133509727";
    private static final long BASE_DELAY_MILLIS = 1000;
    private static final long MAX_DELAY_MILLIS = 120_000;
    // The scheduler is given an absolute Instant (now + delay), so measured delays are slightly under the real one.
    private static final long DELAY_TOLERANCE_MILLIS = 400;

    @Mock
    private Connector connector;
    @Mock
    private TaskScheduler scheduler;
    @Mock
    private CursorService cursorService;
    @Mock
    private NpmPackageService npmPackageService;
    @Mock
    private NpmElasticSearchSyncService npmElasticSearchSyncService;
    @Mock
    private ScheduledFuture<?> scheduledFuture;

    @Captor
    private ArgumentCaptor<Runnable> taskCaptor;
    @Captor
    private ArgumentCaptor<Instant> instantCaptor;
    @Captor
    private ArgumentCaptor<ConnectorCursor> connectorCursorCaptor;
    @Captor
    private ArgumentCaptor<List<NpmPackage>> packagesCaptor;

    private NpmIngestProcess process;

    @BeforeEach
    void setUp() {
        lenient().doReturn(scheduledFuture).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
        process = new NpmIngestProcess(connector, scheduler, cursorService, npmPackageService, npmElasticSearchSyncService);
    }

    // ---- lifecycle ----

    @Test
    void startSchedulesTheFirstRunWithoutFetchingAnything() {
        process.start();

        verify(scheduler).schedule(any(Runnable.class), any(Instant.class));
        verifyNoInteractions(connector);
    }

    @Test
    void stopCancelsTheNextRunAndStopsRescheduling() {
        process.start();

        process.stop();
        runScheduledTask();

        verify(scheduledFuture).cancel(false);
        verifyNoInteractions(connector);
        verify(scheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void stopBeforeStartDoesNotThrow() {
        assertThatCode(process::stop).doesNotThrowAnyException();
    }

    // ---- cursor ----

    @Test
    void usesTheDefaultCursorWhenNoneIsStored() {
        when(cursorService.getCursorForSource(CURSOR_SOURCE)).thenReturn(Optional.empty());
        givenPage(page("200"));
        process.start();

        runScheduledTask();

        verify(connector).getChangePage(connectorCursorCaptor.capture(), anyInt());
        assertThat(connectorCursorCaptor.getValue().cursorValue()).isEqualTo(DEFAULT_CURSOR_VALUE);
    }

    @Test
    void usesTheStoredCursorWhenOneExists() {
        Cursor stored = new Cursor();
        stored.setSource(CURSOR_SOURCE);
        stored.setCursorValue("777");
        when(cursorService.getCursorForSource(CURSOR_SOURCE)).thenReturn(Optional.of(stored));
        givenPage(page("800"));
        process.start();

        runScheduledTask();

        verify(connector).getChangePage(connectorCursorCaptor.capture(), anyInt());
        assertThat(connectorCursorCaptor.getValue().cursorValue()).isEqualTo("777");
    }

    @Test
    void savesTheCursorFromTheReturnedPageOnlyAfterBothStoresSucceed() {
        givenPage(page("200", updateEvent("a"), deleteEvent("b")));
        process.start();

        runScheduledTask();

        InOrder order = inOrder(npmPackageService, npmElasticSearchSyncService, cursorService);
        order.verify(npmPackageService).syncUpdatedDeletedPackagesFromIngest(anyList());
        order.verify(npmPackageService).syncDeletedPackagesFromIngest(anyList());
        order.verify(npmElasticSearchSyncService).updateNpmPackages(anyList());
        order.verify(npmElasticSearchSyncService).removeNpmPackages(anyList());
        order.verify(cursorService).updateOrCreateCursor(CURSOR_SOURCE, "200");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void doesNotSaveANullOrBlankCursor(String nextCursor) {
        givenPage(page(nextCursor));
        process.start();

        runScheduledTask();

        verify(cursorService, never()).updateOrCreateCursor(anyString(), anyString());
    }

    // ---- event routing ----

    @Test
    void routesUpdatesAndDeletesToTheirOwnPaths() {
        givenPage(page("200", updateEvent("a"), deleteEvent("b"), updateEvent("c")));
        when(npmPackageService.syncUpdatedDeletedPackagesFromIngest(anyList())).thenAnswer(inv -> inv.getArgument(0));
        process.start();

        runScheduledTask();

        verify(npmPackageService).syncUpdatedDeletedPackagesFromIngest(packagesCaptor.capture());
        assertThat(packagesCaptor.getValue()).extracting(NpmPackage::getPackageId).containsExactly("a", "c");
        verify(npmPackageService).syncDeletedPackagesFromIngest(List.of("b"));
        verify(npmElasticSearchSyncService).removeNpmPackages(List.of("b"));
        verify(npmElasticSearchSyncService).updateNpmPackages(packagesCaptor.capture());
        assertThat(packagesCaptor.getValue()).extracting(NpmPackage::getPackageId).containsExactly("a", "c");
    }

    @Test
    void mapsContentFieldsToThePackageDomainObject() {
        givenPage(page("200", updateEvent("a")));
        process.start();

        runScheduledTask();

        verify(npmPackageService).syncUpdatedDeletedPackagesFromIngest(packagesCaptor.capture());
        NpmPackage mapped = packagesCaptor.getValue().getFirst();
        assertThat(mapped.getPackageId()).isEqualTo("a");
        assertThat(mapped.getName()).isEqualTo("a");
        assertThat(mapped.isDeleted()).isFalse();
        assertThat(mapped.getLatestVersion()).isEqualTo("1.0.0");
        assertThat(mapped.getDescription()).isEqualTo("description of a");
        assertThat(mapped.getKeywords()).containsExactly("one");
        assertThat(mapped.getLicense()).isEqualTo("MIT");
        assertThat(mapped.getDatePackageCreated()).isEqualTo(T0);
        assertThat(mapped.getDatePackageModified()).isEqualTo(T0.plusDays(1));
        assertThat(mapped.getDateLatestVersionModified()).isEqualTo(T0.plusDays(2));
    }

    @Test
    void usesTheEpochPlaceholderWhenCreatedOrModifiedDatesAreMissing() {
        NpmPackageEventChangeContent content = updateContent("a");
        content.setDateCreated(null);
        content.setDateModified(null);
        content.setDateLatestVersionModified(null);
        givenPage(page("200", event(content)));
        process.start();

        runScheduledTask();

        verify(npmPackageService).syncUpdatedDeletedPackagesFromIngest(packagesCaptor.capture());
        NpmPackage mapped = packagesCaptor.getValue().getFirst();
        assertThat(mapped.getDatePackageCreated().toInstant()).isEqualTo(Instant.EPOCH);
        assertThat(mapped.getDatePackageModified().toInstant()).isEqualTo(Instant.EPOCH);
        assertThat(mapped.getDateLatestVersionModified()).isNull();
    }

    @Test
    void anEmptyPageStillAdvancesTheCursorAndReschedules() {
        givenPage(page("300"));
        process.start();

        runScheduledTask();

        verify(cursorService).updateOrCreateCursor(CURSOR_SOURCE, "300");
        verify(scheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void sendsOnlyThePackagesPostgresActuallyReturnedToElasticsearch() {
        givenPage(page("200", updateEvent("fresh"), updateEvent("stale")));
        NpmPackage fresh = new NpmPackage();
        fresh.setPackageId("fresh");
        when(npmPackageService.syncUpdatedDeletedPackagesFromIngest(anyList())).thenReturn(List.of(fresh));
        process.start();

        runScheduledTask();

        verify(npmElasticSearchSyncService).updateNpmPackages(List.of(fresh));
    }

    // ---- failure and backoff ----

    @Test
    void doesNotAdvanceTheCursorWhenTheConnectorFails() {
        when(connector.getChangePage(any(ConnectorCursor.class), anyInt())).thenThrow(new IllegalStateException("npm is down"));
        process.start();

        runScheduledTask();

        verify(cursorService, never()).updateOrCreateCursor(anyString(), anyString());
        verifyNoInteractions(npmPackageService, npmElasticSearchSyncService);
    }

    @Test
    void doesNotAdvanceTheCursorWhenElasticsearchSyncFails() {
        givenPage(page("200", updateEvent("a")));
        doThrow(new ElasticSearchSyncRequestException("bulk failed")).when(npmElasticSearchSyncService).updateNpmPackages(anyList());
        process.start();

        runScheduledTask();

        // Postgres was already written; the same page is fetched again next run and upserts are idempotent.
        verify(npmPackageService).syncUpdatedDeletedPackagesFromIngest(anyList());
        verify(cursorService, never()).updateOrCreateCursor(anyString(), anyString());
    }

    @Test
    void reschedulesAfterAFailureSoTheLoopKeepsRunning() {
        when(connector.getChangePage(any(ConnectorCursor.class), anyInt())).thenThrow(new IllegalStateException("npm is down"));
        process.start();

        runScheduledTask();

        verify(scheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void backoffDoublesOnConsecutiveFailuresAndResetsAfterASuccess() {
        IllegalStateException failure = new IllegalStateException("npm is down");
        when(connector.getChangePage(any(ConnectorCursor.class), anyInt()))
                .thenThrow(failure, failure, failure)
                .thenReturn(page("200"));
        process.start();

        runScheduledTask();
        assertDelayAbout(2 * BASE_DELAY_MILLIS);
        runScheduledTask();
        assertDelayAbout(4 * BASE_DELAY_MILLIS);
        runScheduledTask();
        assertDelayAbout(8 * BASE_DELAY_MILLIS);
        runScheduledTask();
        assertDelayAbout(BASE_DELAY_MILLIS);
    }

    @Test
    void backoffIsCappedAtTheMaximumDelay() {
        when(connector.getChangePage(any(ConnectorCursor.class), anyInt())).thenThrow(new IllegalStateException("npm is down"));
        process.start();

        // 2, 4, 8, 16, 32, 64, then 128 is clamped to 120 seconds and stays there.
        for (int i = 0; i < 6; i++) runScheduledTask();
        assertDelayAbout(64 * BASE_DELAY_MILLIS);
        runScheduledTask();
        assertDelayAbout(MAX_DELAY_MILLIS);
        runScheduledTask();
        assertDelayAbout(MAX_DELAY_MILLIS);
        runScheduledTask();
        assertDelayAbout(MAX_DELAY_MILLIS);
    }

    // ---- helpers ----

    private void givenPage(ChangeEventPage page) {
        when(connector.getChangePage(any(ConnectorCursor.class), anyInt())).thenReturn(page);
    }

    // Runs the most recent Runnable given to the scheduler (the next page of the loop).
    private void runScheduledTask() {
        verify(scheduler, atLeastOnce()).schedule(taskCaptor.capture(), any(Instant.class));
        taskCaptor.getValue().run();
    }

    private void assertDelayAbout(long expectedMillis) {
        verify(scheduler, atLeastOnce()).schedule(any(Runnable.class), instantCaptor.capture());
        long delay = instantCaptor.getValue().toEpochMilli() - System.currentTimeMillis();
        assertThat(delay).isBetween(expectedMillis - DELAY_TOLERANCE_MILLIS, expectedMillis);
    }

    private static ChangeEventPage page(String nextCursor, ChangeEvent... events) {
        return new ChangeEventPage(new ConnectorCursor(nextCursor), List.of(events));
    }

    private static ChangeEvent updateEvent(String id) {
        return event(updateContent(id));
    }

    private static NpmPackageEventChangeContent updateContent(String id) {
        NpmPackageEventChangeContent content = new NpmPackageEventChangeContent();
        content.setId(id);
        content.setName(id);
        content.setLatestVersion("1.0.0");
        content.setDescription("description of " + id);
        content.setKeywords(List.of("one"));
        content.setLicense("MIT");
        content.setDateCreated(T0);
        content.setDateModified(T0.plusDays(1));
        content.setDateLatestVersionModified(T0.plusDays(2));
        return content;
    }

    private static ChangeEvent deleteEvent(String id) {
        NpmPackageEventChangeContent content = new NpmPackageEventChangeContent();
        content.setId(id);
        content.setDeleted(true);
        return event(content);
    }

    private static ChangeEvent event(NpmPackageEventChangeContent content) {
        ChangeEvent event = new ChangeEvent();
        event.setContent(content);
        return event;
    }
}
