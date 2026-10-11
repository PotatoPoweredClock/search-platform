package com.searchplatform.search.service.shared;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"totalResults", "items"})
public record SearchServiceResponse<T>(List<SearchServiceResponseItem<T>> items, long totalResults) {
}
