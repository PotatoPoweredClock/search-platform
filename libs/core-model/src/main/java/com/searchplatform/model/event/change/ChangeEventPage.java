package com.searchplatform.model.event.change;

import com.searchplatform.model.connector.Cursor;

import java.util.List;

public record ChangeEventPage(Cursor cursor, List<ChangeEvent> events){}