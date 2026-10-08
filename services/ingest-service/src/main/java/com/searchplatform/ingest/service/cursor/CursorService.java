package com.searchplatform.ingest.service.cursor;

import com.searchplatform.ingest.domain.cursor.Cursor;
import com.searchplatform.ingest.repository.cursor.CursorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CursorService {
    private final CursorRepository cursorRepository;

    public Optional<Cursor> getCursorForSource(String source) {
        return cursorRepository.findById(source);
    }

    public void updateOrCreateCursor(String source, String cursorValue) {
        cursorRepository.upsert(source, cursorValue);
    }
}
