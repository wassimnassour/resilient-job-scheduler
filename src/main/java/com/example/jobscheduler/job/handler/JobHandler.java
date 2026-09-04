package com.example.jobscheduler.job.handler;

import tools.jackson.databind.JsonNode;

public interface JobHandler {
    String getType();

    void execute(JsonNode payload) throws Exception;
}
