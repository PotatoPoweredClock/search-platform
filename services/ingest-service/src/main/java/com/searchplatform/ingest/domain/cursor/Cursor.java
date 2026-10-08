package com.searchplatform.ingest.domain.cursor;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "cursor_storage")
public class Cursor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(unique = true, nullable = false)
    private String source;
    @Column(nullable = false)
    private String cursorValue;

}
