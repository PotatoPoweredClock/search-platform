package com.searchplatform.model.event.change;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeEvent {
    private ChangeSourceContent content;
}
