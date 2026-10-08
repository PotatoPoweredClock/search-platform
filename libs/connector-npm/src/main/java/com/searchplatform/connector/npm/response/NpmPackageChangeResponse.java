package com.searchplatform.connector.npm.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class NpmPackageChangeResponse {
    @EqualsAndHashCode.Include
    private String id;
    @JsonProperty("seq")
    private String sequence;
    private boolean deleted = false;
}
