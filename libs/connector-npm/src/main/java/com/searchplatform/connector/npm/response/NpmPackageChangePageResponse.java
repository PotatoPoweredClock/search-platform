package com.searchplatform.connector.npm.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class NpmPackageChangePageResponse {
    private List<NpmPackageChangeResponse> results;
    @JsonProperty("last_seq")
    private String lastSequence;
}
