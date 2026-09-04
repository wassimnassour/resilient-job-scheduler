package com.example.jobscheduler.job.handler;

import com.example.jobscheduler.job.enums.EJobStatus;
import com.example.jobscheduler.job.enums.EJobType;
import tools.jackson.databind.JsonNode;

public interface JobHandler {
    EJobType getType();

    void execute(JsonNode payload) throws Exception;
}
