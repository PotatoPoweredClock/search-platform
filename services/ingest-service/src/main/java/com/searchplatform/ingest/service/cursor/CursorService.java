package com.searchplatform.ingest.service.cursor;

import com.searchplatform.ingest.domain.cursor.Cursor;
import com.searchplatform.ingest.repository.cursor.CursorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CursorService {
    private final CursorRepository cursorRepository;


    public Cursor getCursorForSource(String source) {
        return cursorRepository.findFirstBySource(source);
    }

    public Cursor updateOrCreateCursor(String source, String cursorValue) {
        Cursor cursor = cursorRepository.findFirstBySource(source);
        if (cursor == null) {
            cursor = new Cursor();
            cursor.setSource(source);
        }
        cursor.setCursorValue(cursorValue);
        return cursorRepository.save(cursor);
    }
}
