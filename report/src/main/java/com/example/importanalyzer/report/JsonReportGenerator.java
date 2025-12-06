package com.example.importanalyzer.report;

import com.example.importanalyzer.core.ImportIssue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.List;

public class JsonReportGenerator {
    private final ObjectMapper mapper;

    public JsonReportGenerator(boolean pretty) {
        this.mapper = new ObjectMapper();
        if (pretty) {
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
        }
    }

    public String toJson(List<ImportIssue> issues) {
        try {
            return mapper.writeValueAsString(issues);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to render JSON", e);
        }
    }
}
