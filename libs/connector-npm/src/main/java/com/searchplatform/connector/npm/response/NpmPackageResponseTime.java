package com.searchplatform.connector.npm.response;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class NpmPackageResponseTime {
    private String created;
    private String modified;
    private Unpublished unpublished;

    // Every other key is a version number mapped to its publish timestamp.
    @JsonAnySetter
    private Map<String, String> versionTimes = new HashMap<>();

    public String getVersionTime(String version) {
        return versionTimes.get(version);
    }

    @Getter
    @Setter
    public static class Unpublished {
        private String time;
        private List<String> versions;
    }
}
