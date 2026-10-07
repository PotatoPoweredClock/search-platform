package com.searchplatform.connector.npm.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class NpmPackageFullResponse {
    @JsonProperty("_id")
    private String id;
    private String name;
    @JsonProperty("dist-tags")
    private Map<String,String> distTags;
    private List<String> keywords;
    private String description;
    private String license;
    private NpmPackageResponseTime time;
}

