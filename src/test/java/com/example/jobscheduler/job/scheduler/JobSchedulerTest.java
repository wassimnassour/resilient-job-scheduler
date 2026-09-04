package com.example.jobscheduler.job.scheduler;

import com.example.jobscheduler.job.domain.Job;
import com.example.jobscheduler.job.enums.EJobStatus;
import com.example.jobscheduler.job.enums.EJobType;
import com.example.jobscheduler.job.handler.JobHandler;
import com.example.jobscheduler.job.handler.JobHandlerRegistry;
import com.example.jobscheduler.job.repository.JobRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobSchedulerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pollDueJobs_executesTheStoredPayloadAndMarksTheJobSucceeded() throws Exception {
        JobRepository repository = mock(JobRepository.class);
        JobHandlerRegistry registry = mock(JobHandlerRegistry.class);
        JobHandler handler = mock(JobHandler.class);
        JsonNode payload = objectMapper.readTree("{\"message\":\"from the database\"}");
        Job job = job(payload);

        when(repository.findReadyJobs(any(), any(), any())).thenReturn(List.of(job));
        when(registry.getHandler(EJobType.LOG)).thenReturn(handler);

        new JobScheduler(registry, repository, 50).pollDueJobs();

        verify(handler).execute(payload);
        verify(repository, times(2)).save(job);
        org.junit.jupiter.api.Assertions.assertEquals(EJobStatus.SUCCEEDED, job.getStatus());
    }

    @Test
    void pollDueJobs_marksFailedJobsAndContinuesWithTheNextJob() throws Exception {
        JobRepository repository = mock(JobRepository.class);
        JobHandlerRegistry registry = mock(JobHandlerRegistry.class);
        JobHandler handler = mock(JobHandler.class);
        Job failedJob = job(objectMapper.readTree("{\"id\":1}"));
        Job succeedingJob = job(objectMapper.readTree("{\"id\":2}"));

        when(repository.findReadyJobs(any(), any(), any())).thenReturn(List.of(failedJob, succeedingJob));
        when(registry.getHandler(EJobType.LOG)).thenReturn(handler);
        doThrow(new IllegalStateException("handler failed"))
                .doNothing()
                .when(handler)
                .execute(any(JsonNode.class));

        new JobScheduler(registry, repository, 50).pollDueJobs();

        verify(handler).execute(failedJob.getPayload());
        verify(handler).execute(succeedingJob.getPayload());
        verify(repository, times(2)).save(failedJob);
        verify(repository, times(2)).save(succeedingJob);
        org.junit.jupiter.api.Assertions.assertEquals(EJobStatus.FAILED, failedJob.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(EJobStatus.SUCCEEDED, succeedingJob.getStatus());
    }

    private Job job(JsonNode payload) {
        Job job = new Job();
        job.setType(EJobType.LOG);
        job.setStatus(EJobStatus.PENDING);
        job.setPayload(payload);
        job.setScheduledAt(Instant.now());
        return job;
    }
}
