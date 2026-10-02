package com.searchplatform.connector;

import com.searchplatform.model.connector.Cursor;
import com.searchplatform.model.event.change.ChangeEventPage;

public interface Connector {

    ChangeEventPage getChangePage(Cursor cursor, int limit);
}
