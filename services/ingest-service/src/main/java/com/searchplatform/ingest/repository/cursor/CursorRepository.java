package com.searchplatform.ingest.repository.cursor;

import com.searchplatform.ingest.domain.cursor.Cursor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CursorRepository extends JpaRepository<Cursor, String> {

    @Modifying
    @Transactional
    @Query(
            value = """
                    INSERT INTO cursor_storage (source, cursor_value) 
                        VALUES(:source, :cursorValue)
                        ON CONFLICT(source) DO UPDATE SET
                        cursor_value = EXCLUDED.cursor_value
                    """, nativeQuery = true
    )
    void upsert(@Param("source") String source, @Param("cursorValue") String cursorValue);
}
