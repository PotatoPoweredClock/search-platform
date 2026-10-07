package com.searchplatform.model.event.change;

import com.searchplatform.model.connector.ConnectorCursor;

import java.util.List;

public record ChangeEventPage(ConnectorCursor cursor, List<ChangeEvent> events){}