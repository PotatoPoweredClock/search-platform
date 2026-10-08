package com.searchplatform.ingest.domain.cursor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "cursor_storage")
public class Cursor {
    @Id
    private String source;
    @Column(nullable = false)
    private String cursorValue;

}
