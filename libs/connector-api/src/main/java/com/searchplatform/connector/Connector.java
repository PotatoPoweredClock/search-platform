package com.searchplatform.connector;

import com.searchplatform.model.connector.ConnectorCursor;
import com.searchplatform.model.event.change.ChangeEventPage;

public interface Connector {

    ChangeEventPage getChangePage(ConnectorCursor cursor, int limit);
}
