package com.searchplatform.search.controller.npm;

import com.searchplatform.search.service.npm.NpmPackageSearchResponse;
import com.searchplatform.search.service.npm.NpmSearchService;
import com.searchplatform.search.service.shared.SearchServiceResponse;
import com.searchplatform.search.service.shared.SearchServiceResponseItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NpmPackageSearchController.class)
public class NpmPackageSearchControllerTest {
    private static final String BASE_PATH = "/api/v1/npm/packages";
    private static final String QUERY = "someQuery";
    private static final String QUERY_PARAM = "query";
    private static final String PAGE_PARAM = "page";
    private static final String PAGE_SIZE_PARAM = "pageSize";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper mapper;

    @MockitoBean
    private NpmSearchService npmSearchService;

    private static final NpmPackageSearchResponse mockSearchDocument = new NpmPackageSearchResponse(
            "packageName",
            "test description",
            List.of("keyword1", "keyword2"),
            "0.0.1",
            "testLicense",
            OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC")),
            OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC")),
            OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC"))
    );

    @Test
    public void getSearchRequestWithoutResults() throws Exception {
        SearchServiceResponse<NpmPackageSearchResponse> response = new SearchServiceResponse<>(new ArrayList<>(), 0);

        when(npmSearchService.query(eq(QUERY), anyInt(), anyInt())).thenReturn(response);

        mockMvc.perform(get(BASE_PATH).param(QUERY_PARAM, QUERY))
                .andExpect(status().isOk())
                .andExpect(content().json(mapper.writeValueAsString(response)));

        verify(npmSearchService).query(eq(QUERY), anyInt(), anyInt());
    }

    @Test
    public void getSearchRequestUsesDefaultValues() throws Exception {
        SearchServiceResponse<NpmPackageSearchResponse> response = new SearchServiceResponse<>(new ArrayList<>(), 0);

        when(npmSearchService.query(eq(QUERY), eq(1), eq(20))).thenReturn(response);

        mockMvc.perform(get(BASE_PATH).param(QUERY_PARAM, QUERY))
                .andExpect(status().isOk())
                .andExpect(content().json(mapper.writeValueAsString(response)));

        verify(npmSearchService).query(QUERY, 1, 20);
    }

    @Test
    public void getSearchRequestUsesPassedValues() throws Exception {
        SearchServiceResponse<NpmPackageSearchResponse> response = new SearchServiceResponse<>(new ArrayList<>(), 0);

        when(npmSearchService.query(eq(QUERY), eq(5), eq(5))).thenReturn(response);

        mockMvc.perform(get(BASE_PATH).param(QUERY_PARAM, QUERY).param(PAGE_PARAM,"5").param(PAGE_SIZE_PARAM, "5"))
                .andExpect(status().isOk())
                .andExpect(content().json(mapper.writeValueAsString(response)));

        verify(npmSearchService).query(QUERY, 5, 5);
    }

    @Test
    public void getSearchRequestTrimsQuery() throws Exception {
        String query = "   " + QUERY + "   ";
        SearchServiceResponse<NpmPackageSearchResponse> response = new SearchServiceResponse<>(new ArrayList<>(), 0);

        when(npmSearchService.query(eq(QUERY), eq(5), eq(5))).thenReturn(response);

        mockMvc.perform(get(BASE_PATH).param(QUERY_PARAM, query).param(PAGE_PARAM,"5").param(PAGE_SIZE_PARAM, "5"))
                .andExpect(status().isOk())
                .andExpect(content().json(mapper.writeValueAsString(response)));

        verify(npmSearchService).query(QUERY, 5, 5);
    }

    @Test
    public void getSearchRequestWithResults() throws Exception {
        SearchServiceResponse<NpmPackageSearchResponse> response = new SearchServiceResponse<>(List.of(new SearchServiceResponseItem<>("testId", mockSearchDocument, null)), 1);

        when(npmSearchService.query(eq(QUERY), eq(1), eq(20))).thenReturn(response);

        mockMvc.perform(get(BASE_PATH).param(QUERY_PARAM, QUERY))
                .andExpect(status().isOk())
                .andExpect(content().json(mapper.writeValueAsString(response)));

        verify(npmSearchService).query(QUERY, 1, 20);
    }

    @Test
    public void getSearchRequestErrorWithNoQuery() throws Exception {
        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isBadRequest());

        verify(npmSearchService, never()).query(any(), anyInt(), anyInt());
    }

    @Test
    public void getSearchRequestErrorWithQueryBelowMinValue() throws Exception {
        mockMvc.perform(get(BASE_PATH).param(QUERY_PARAM,"a"))
                .andExpect(status().isBadRequest());

        verify(npmSearchService, never()).query(any(), anyInt(), anyInt());
    }

    @Test
    public void getSearchRequestErrorWithQueryTooLarge() throws Exception {
        mockMvc.perform(get(BASE_PATH).param(QUERY_PARAM,"a".repeat(101)))
                .andExpect(status().isBadRequest());

        verify(npmSearchService, never()).query(any(), anyInt(), anyInt());
    }

    @Test
    public void getSearchRequestErrorWithPageTooSmall() throws Exception {
        mockMvc.perform(get(BASE_PATH).param(QUERY_PARAM, QUERY).param(PAGE_PARAM,"0"))
                .andExpect(status().isBadRequest());

        verify(npmSearchService, never()).query(any(), anyInt(), anyInt());
    }

    @Test
    public void getSearchRequestErrorWithPageTooBig() throws Exception {
        mockMvc.perform(get(BASE_PATH).param(QUERY_PARAM, QUERY).param(PAGE_PARAM,"10001"))
                .andExpect(status().isBadRequest());

        verify(npmSearchService, never()).query(any(), anyInt(), anyInt());
    }

    @Test
    public void getSearchRequestErrorWithPageSizeTooSmall() throws Exception {
        mockMvc.perform(get(BASE_PATH).param(QUERY_PARAM, QUERY).param(PAGE_SIZE_PARAM,"0"))
                .andExpect(status().isBadRequest());

        verify(npmSearchService, never()).query(any(), anyInt(), anyInt());
    }

    @Test
    public void getSearchRequestErrorWithPageSizeTooBig() throws Exception {
        mockMvc.perform(get(BASE_PATH).param(QUERY_PARAM, QUERY).param(PAGE_SIZE_PARAM,"101"))
                .andExpect(status().isBadRequest());

        verify(npmSearchService, never()).query(any(), anyInt(), anyInt());
    }

    @Test
    public void getSearchRequestErrorOverMaxResultsWindow() throws Exception {
        mockMvc.perform(get(BASE_PATH).param(QUERY_PARAM, QUERY).param(PAGE_SIZE_PARAM,"2").param(PAGE_PARAM,"5001"))
                .andExpect(status().isBadRequest());

        verify(npmSearchService, never()).query(any(), anyInt(), anyInt());
    }
}
