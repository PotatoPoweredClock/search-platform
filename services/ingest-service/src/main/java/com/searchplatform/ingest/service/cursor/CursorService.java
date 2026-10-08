package com.searchplatform.ingest.service.cursor;

import com.searchplatform.ingest.domain.cursor.Cursor;
import com.searchplatform.ingest.repository.cursor.CursorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CursorService {
    private final CursorRepository cursorRepository;

    public Cursor getCursorForSource(String source) {
        return cursorRepository.getReferenceById(source);
    }

    //TODO: change to upsert
    public void updateOrCreateCursor(String source, String cursorValue) {
        Cursor cursor = null;
        try {
            cursor = cursorRepository.getReferenceById(source);
        } catch (EntityNotFoundException e) {
            // do nothing.
        }

        if (cursor == null) {
            cursor = new Cursor();
            cursor.setSource(source);
        }
        cursor.setCursorValue(cursorValue);
        cursorRepository.save(cursor);
        return;
    }
}
