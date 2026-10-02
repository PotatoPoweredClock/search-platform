package com.searchplatform.connector.npm.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NpmPackageChangeResponse {
    private String id;
    @JsonProperty("seq")
    private String sequence;
    private boolean deleted = false;
}
