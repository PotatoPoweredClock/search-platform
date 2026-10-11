package com.searchplatform.search.controller.npm;

import com.searchplatform.search.service.npm.NpmPackageSearchResponse;
import com.searchplatform.search.service.npm.NpmSearchService;
import com.searchplatform.search.service.shared.SearchServiceResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;


@RestController
@RequestMapping("/api/v1/npm/packages")
@AllArgsConstructor
public class NpmPackageSearchController {
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_RESULT_WINDOW = 10000;

    private final NpmSearchService npmSearchService;

    @GetMapping()
    public ResponseEntity<SearchServiceResponse<NpmPackageSearchResponse>> npmPackageSearch(
            @RequestParam(value = "query", required = true) @NotBlank String query,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) @Max(MAX_RESULT_WINDOW) int page,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int pageSize) {
        String trimmed = query.trim();
        if (trimmed.length() <= MIN_QUERY_LENGTH || trimmed.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must be between %d and %d characters.".formatted(MIN_QUERY_LENGTH, MAX_QUERY_LENGTH));
        }
        if (page * pageSize > MAX_RESULT_WINDOW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Results beyond the first %d are not reachable; narrow the query.".formatted(MAX_RESULT_WINDOW));
        }
        return ResponseEntity.ok(npmSearchService.query(trimmed, page, pageSize));
    }
}
