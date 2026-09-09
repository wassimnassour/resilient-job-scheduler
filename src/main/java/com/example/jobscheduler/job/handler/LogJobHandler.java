package com.example.jobscheduler.job.handler;

import com.example.jobscheduler.job.enums.EJobStatus;
import com.example.jobscheduler.job.enums.EJobType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.concurrent.TimeoutException;

@Component
public class LogJobHandler implements JobHandler {

    private static final Logger logger = LoggerFactory.getLogger(LogJobHandler.class);

    @Override
    public EJobType getType() {
        return EJobType.LOG;
    }

    @Override
    public void execute(JsonNode payload) throws InterruptedException {
        Thread.sleep(50000);
        logger.info("Executing LOG job with payload: {}", payload);
    }
}
