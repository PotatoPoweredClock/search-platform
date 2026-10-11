package com.searchplatform.search.service.shared;

import java.util.Map;

public record SearchServiceResponseItem<T>(String id, T source, Map<String, String> attributes) {
}
