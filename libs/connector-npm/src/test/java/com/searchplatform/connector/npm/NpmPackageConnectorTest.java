package com.searchplatform.connector.npm;


import com.searchplatform.connector.npm.response.NpmPackageChangePageResponse;
import com.searchplatform.connector.npm.response.NpmPackageChangeResponse;
import com.searchplatform.connector.npm.response.NpmPackageFullResponse;
import com.searchplatform.model.connector.Cursor;
import com.searchplatform.model.event.change.ChangeEventPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NpmPackageConnectorTest {


    private static NpmPackageChangePageResponse threeChangesSecondDeleted() {
        NpmPackageChangeResponse first = new NpmPackageChangeResponse();
        first.setId("package1");
        first.setSequence("133509741");

        NpmPackageChangeResponse second = new NpmPackageChangeResponse();
        second.setId("package2");
        second.setSequence("133509743");
        second.setDeleted(true);

        NpmPackageChangeResponse third = new NpmPackageChangeResponse();
        third.setId("package3");
        third.setSequence("133509745");

        NpmPackageChangePageResponse page = new NpmPackageChangePageResponse();
        page.setResults(List.of(first, second, third));
        page.setLastSequence("133509749");
        return page;
    }

    private static NpmPackageFullResponse packageOneFull() {
        NpmPackageFullResponse full = new NpmPackageFullResponse();
        full.setId("package1");
        full.setName("package1Name");
        full.setDistTags(Map.of("latest", "0.21.0"));
        full.setKeywords(List.of("keyword1", "keyword2"));
        full.setDescription("Description here.");
        full.setLicense("AGPL-3.0-or-later");
        full.setTime(Map.of(
                "created", "2026-08-01T19:20:42.380Z",
                "modified", "2026-09-30T01:56:34.655Z",
                "2.0.0", "2026-08-01T19:20:42.764Z",
                "0.21.0", "2026-09-30T01:56:34.318Z"));
        return full;
    }

    @Mock
    ConnectorClientService mockService;

    private NpmPackageConnector connector;

    @BeforeEach
    public void before() {
        connector = new NpmPackageConnector(mockService);
    }

    @Test
    public void successfullyGetsAndMapsResultsAndHandles404Package() {
        HttpClientErrorException notFoundException = new HttpClientErrorException(HttpStatusCode.valueOf(404));

        doReturn(threeChangesSecondDeleted())
                .when(mockService)
                .makeRequest(
                        argThat(uri -> uri.toString().contains("/_changes")),
                        eq(NpmPackageChangePageResponse.class));

        doReturn(packageOneFull())
                .when(mockService)
                .makeRequest(
                        argThat(uri -> uri.toString().contains("/package1")),
                        eq(NpmPackageFullResponse.class));

        doThrow(notFoundException)
                .when(mockService)
                .makeRequest(
                        argThat(uri -> uri.toString().contains("/package3")),
                        eq(NpmPackageFullResponse.class));


        ChangeEventPage results = connector.getChangePage(new Cursor("1"), 4);

        //one call for page
        verify(mockService, times(1)).makeRequest(
                any(), eq(NpmPackageChangePageResponse.class));

        //two calls for package
        verify(mockService, times(2)).makeRequest(
                any(), eq(NpmPackageFullResponse.class));


        //verify results
        assertNotNull(results);
        assertEquals("133509749", results.cursor().cursorValue());
        assertEquals(3, results.events().size());

        PackageEventChangeContent packageOne = (PackageEventChangeContent) results.events().get(0).getContent();
        PackageEventChangeContent packageTwo = (PackageEventChangeContent) results.events().get(1).getContent();
        PackageEventChangeContent packageThree = (PackageEventChangeContent) results.events().get(2).getContent();

        //validate package one
        assertThat(packageOne.getId()).isEqualTo("package1");
        assertFalse(packageOne.isDeleted());
        assertThat(packageOne.getName()).isEqualTo("package1Name");
        assertThat(packageOne.getLatestVersion()).isEqualTo("0.21.0");
        assertThat(packageOne.getKeywords()).containsExactly("keyword1", "keyword2");
        assertThat(packageOne.getLicense()).isEqualTo("AGPL-3.0-or-later");
        assertThat(packageOne.getDateCreated()).isEqualTo(OffsetDateTime.parse("2026-08-01T19:20:42.380Z"));

        //validate package two
        assertThat(packageTwo.getId()).isEqualTo("package2");
        assertNull(packageTwo.getName());
        assertTrue(packageTwo.isDeleted());

        //validate package three
        assertThat(packageThree.getId()).isEqualTo("package3");
        assertNull(packageThree.getName());
        assertTrue(packageThree.isDeleted());
    }

    @Test
    public void pageRequestErrorThrowsException() {
        HttpClientErrorException badRequestException = new HttpClientErrorException(HttpStatusCode.valueOf(400));

        doThrow(badRequestException)
                .when(mockService)
                .makeRequest(
                        argThat(uri -> uri.toString().contains("/_changes")),
                        eq(NpmPackageChangePageResponse.class));

        assertThatThrownBy(() -> connector.getChangePage(new Cursor("1"), 4))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    public void packageRequestErrorThrowsException() {
        HttpClientErrorException rateLimitError = new HttpClientErrorException(HttpStatusCode.valueOf(429));

        doReturn(threeChangesSecondDeleted())
                .when(mockService)
                .makeRequest(
                        argThat(uri -> uri.toString().contains("/_changes")),
                        eq(NpmPackageChangePageResponse.class));

        doReturn(packageOneFull())
                .when(mockService)
                .makeRequest(
                        argThat(uri -> uri.toString().contains("/package1")),
                        eq(NpmPackageFullResponse.class));

        doThrow(rateLimitError)
                .when(mockService)
                .makeRequest(
                        argThat(uri -> uri.toString().contains("/package3")),
                        eq(NpmPackageFullResponse.class));


        assertThatThrownBy(() -> connector.getChangePage(new Cursor("1"), 4))
                .isInstanceOf(HttpClientErrorException.class);

        //one call for page
        verify(mockService, times(1)).makeRequest(
                any(), eq(NpmPackageChangePageResponse.class));

        //two calls for package
        verify(mockService, times(2)).makeRequest(
                any(), eq(NpmPackageFullResponse.class));

    }

    @Test
    public void packageServerErrorThrowsException() {
        stubPageAndPackageOne();

        doThrow(new HttpServerErrorException(HttpStatusCode.valueOf(500)))
                .when(mockService)
                .makeRequest(
                        argThat(uri -> uri.toString().contains("/package3")),
                        eq(NpmPackageFullResponse.class));

        assertThatThrownBy(() -> connector.getChangePage(new Cursor("1"), 4))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    public void packageNetworkErrorThrowsException() {
        stubPageAndPackageOne();

        doThrow(new ResourceAccessException("timeout", new IOException("timeout")))
                .when(mockService)
                .makeRequest(
                        argThat(uri -> uri.toString().contains("/package3")),
                        eq(NpmPackageFullResponse.class));

        assertThatThrownBy(() -> connector.getChangePage(new Cursor("1"), 4))
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    public void packageWithMalformedDataIsSkipped() {
        doReturn(threeChangesSecondDeleted())
                .when(mockService)
                .makeRequest(
                        argThat(uri -> uri.toString().contains("/_changes")),
                        eq(NpmPackageChangePageResponse.class));

        // same shape RestClient produces when the converter fails
        RestClientException malformed = new RestClientException("Error while extracting response",
                new HttpMessageNotReadableException("bad json", mock(HttpInputMessage.class)));
        doThrow(malformed)
                .when(mockService)
                .makeRequest(
                        argThat(uri -> uri.toString().contains("/package1")),
                        eq(NpmPackageFullResponse.class));

        doThrow(new HttpClientErrorException(HttpStatusCode.valueOf(404)))
                .when(mockService)
                .makeRequest(
                        argThat(uri -> uri.toString().contains("/package3")),
                        eq(NpmPackageFullResponse.class));

        ChangeEventPage results = connector.getChangePage(new Cursor("1"), 4);

        // package1 is dropped, the cursor still advances past it
        assertEquals("133509749", results.cursor().cursorValue());
        assertEquals(2, results.events().size());
        assertThat(((PackageEventChangeContent) results.events().get(0).getContent()).getId()).isEqualTo("package2");
        assertThat(((PackageEventChangeContent) results.events().get(1).getContent()).getId()).isEqualTo("package3");
    }

    @Test
    public void restClientExceptionWithoutMappingCauseIsRethrown() {
        stubPageAndPackageOne();

        doThrow(new RestClientException("something else"))
                .when(mockService)
                .makeRequest(
                        argThat(uri -> uri.toString().contains("/package3")),
                        eq(NpmPackageFullResponse.class));

        assertThatThrownBy(() -> connector.getChangePage(new Cursor("1"), 4))
                .isInstanceOf(RestClientException.class);
    }

    private void stubPageAndPackageOne() {
        doReturn(threeChangesSecondDeleted())
                .when(mockService)
                .makeRequest(
                        argThat(uri -> uri.toString().contains("/_changes")),
                        eq(NpmPackageChangePageResponse.class));

        doReturn(packageOneFull())
                .when(mockService)
                .makeRequest(
                        argThat(uri -> uri.toString().contains("/package1")),
                        eq(NpmPackageFullResponse.class));
    }

    @Test
    public void testGetChangePageArgumentValidationFailures(){
        assertThatThrownBy(() -> connector.getChangePage(null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connector.getChangePage(new Cursor(null), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connector.getChangePage(new Cursor(""), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> connector.getChangePage(new Cursor("1"), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
