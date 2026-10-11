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
        String query = "someQuery";
        SearchServiceResponse<NpmPackageSearchResponse> response = new SearchServiceResponse<>(new ArrayList<>(), 0);

        when(npmSearchService.query(eq(query), eq(1), eq(20))).thenReturn(response);

        mockMvc.perform(get(BASE_PATH).param("query", query))
                .andExpect(status().isOk())
                .andExpect(content().json(mapper.writeValueAsString(response)));

        verify(npmSearchService).query(query, 1, 20);
    }

    @Test
    public void getSearchRequestUsesDefaultValues() throws Exception {
        String query = "someQuery";
        SearchServiceResponse<NpmPackageSearchResponse> response = new SearchServiceResponse<>(new ArrayList<>(), 0);

        when(npmSearchService.query(eq(query), eq(1), eq(20))).thenReturn(response);

        mockMvc.perform(get(BASE_PATH).param("query", query))
                .andExpect(status().isOk())
                .andExpect(content().json(mapper.writeValueAsString(response)));

        verify(npmSearchService).query(query, 1, 20);
    }

    @Test
    public void getSearchRequestUsesPassedValues() throws Exception {
        String query = "someQuery";
        SearchServiceResponse<NpmPackageSearchResponse> response = new SearchServiceResponse<>(new ArrayList<>(), 0);

        when(npmSearchService.query(eq(query), eq(5), eq(5))).thenReturn(response);

        mockMvc.perform(get(BASE_PATH).param("query", query).param("page","5").param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(content().json(mapper.writeValueAsString(response)));

        verify(npmSearchService).query(query, 5, 5);
    }

    @Test
    public void getSearchRequestTrimsQuery() throws Exception {
        String query = "   someQuery   ";
        String trimmedQuery = "someQuery";
        SearchServiceResponse<NpmPackageSearchResponse> response = new SearchServiceResponse<>(new ArrayList<>(), 0);

        when(npmSearchService.query(eq(trimmedQuery), eq(5), eq(5))).thenReturn(response);

        mockMvc.perform(get(BASE_PATH).param("query", query).param("page","5").param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(content().json(mapper.writeValueAsString(response)));

        verify(npmSearchService).query(trimmedQuery, 5, 5);
    }

    @Test
    public void getSearchRequestWithResults() throws Exception {
        String query = "someQuery";
        SearchServiceResponse<NpmPackageSearchResponse> response = new SearchServiceResponse<>(List.of(new SearchServiceResponseItem<>("testId", mockSearchDocument, null)), 1);

        when(npmSearchService.query(eq(query), eq(1), eq(20))).thenReturn(response);

        mockMvc.perform(get(BASE_PATH).param("query", query))
                .andExpect(status().isOk())
                .andExpect(content().json(mapper.writeValueAsString(response)));

        verify(npmSearchService).query(query, 1, 20);
    }

    @Test
    public void getSearchRequestErrorWithNoQuery() throws Exception {
        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isBadRequest());

        verify(npmSearchService, never()).query(any(), anyInt(), anyInt());
    }

    @Test
    public void getSearchRequestErrorWithQueryBelowMinValue() throws Exception {
        mockMvc.perform(get(BASE_PATH).param("query","a"))
                .andExpect(status().isBadRequest());

        verify(npmSearchService, never()).query(any(), anyInt(), anyInt());
    }

    @Test
    public void getSearchRequestErrorWithQueryToLarge() throws Exception {
        mockMvc.perform(get(BASE_PATH).param("query","a".repeat(101)))
                .andExpect(status().isBadRequest());

        verify(npmSearchService, never()).query(any(), anyInt(), anyInt());
    }

    @Test
    public void getSearchRequestErrorWithPageToSmall() throws Exception {
        mockMvc.perform(get(BASE_PATH).param("query","query").param("page","0"))
                .andExpect(status().isBadRequest());

        verify(npmSearchService, never()).query(any(), anyInt(), anyInt());
    }

    @Test
    public void getSearchRequestErrorWithPageToBig() throws Exception {
        mockMvc.perform(get(BASE_PATH).param("query","query").param("page","10001"))
                .andExpect(status().isBadRequest());

        verify(npmSearchService, never()).query(any(), anyInt(), anyInt());
    }

    @Test
    public void getSearchRequestErrorWithSPageSizeToSmall() throws Exception {
        mockMvc.perform(get(BASE_PATH).param("query","query").param("pageSize","0"))
                .andExpect(status().isBadRequest());

        verify(npmSearchService, never()).query(any(), anyInt(), anyInt());
    }

    @Test
    public void getSearchRequestErrorWithPageSizeToBig() throws Exception {
        mockMvc.perform(get(BASE_PATH).param("query","query").param("pageSize","101"))
                .andExpect(status().isBadRequest());

        verify(npmSearchService, never()).query(any(), anyInt(), anyInt());
    }

    @Test
    public void getSearchRequestErrorOverMaxResultsWindow() throws Exception {
        mockMvc.perform(get(BASE_PATH).param("query","query").param("pageSize","2").param("page","5001"))
                .andExpect(status().isBadRequest());

        verify(npmSearchService, never()).query(any(), anyInt(), anyInt());
    }
}
