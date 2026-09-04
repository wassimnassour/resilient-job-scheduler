package com.example.jobscheduler.job.handler;

import com.example.jobscheduler.job.enums.EJobStatus;
import com.example.jobscheduler.job.enums.EJobType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class LogJobHandler implements JobHandler {

    private static final Logger logger = LoggerFactory.getLogger(LogJobHandler.class);

    @Override
    public EJobType getType() {
        return EJobType.LOG;
    }

    @Override
    public void execute(JsonNode payload) throws Exception {
        logger.info("Executing LOG job with payload: {}", payload);
    }
}
