package com.searchplatform.ingest.repository.cursor;

import com.searchplatform.ingest.domain.cursor.Cursor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CursorRepository extends JpaRepository<Cursor, Integer> {

    public Cursor findFirstBySource(String source);
}
