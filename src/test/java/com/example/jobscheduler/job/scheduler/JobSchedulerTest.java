package com.example.jobscheduler.job.scheduler;

import com.example.jobscheduler.job.domain.Job;
import com.example.jobscheduler.job.enums.EJobStatus;
import com.example.jobscheduler.job.enums.EJobType;
import com.example.jobscheduler.job.handler.JobHandler;
import com.example.jobscheduler.job.handler.JobHandlerRegistry;
import com.example.jobscheduler.job.repository.JobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobSchedulerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<ThreadPoolExecutor> executors = new ArrayList<>();

    @AfterEach
    void shutDownExecutors() {
        executors.forEach(ThreadPoolExecutor::shutdownNow);
    }

    @Test
    void pollDueJobs_executesTheStoredPayloadAndMarksTheJobSucceeded() throws Exception {
        JobRepository repository = mock(JobRepository.class);
        JobHandlerRegistry registry = mock(JobHandlerRegistry.class);
        JobHandler handler = mock(JobHandler.class);
        JsonNode payload = objectMapper.readTree("{\"message\":\"from the database\"}");
        Job job = job(1L, payload);
        CountDownLatch completed = new CountDownLatch(1);

        when(repository.findReadyJobs(any(), any(), any())).thenReturn(List.of(job));
        when(repository.claimJob(any(), any(), any(), any())).thenReturn(1);
        when(repository.findById(job.getId())).thenReturn(Optional.of(job));
        when(registry.getHandler(EJobType.LOG)).thenReturn(handler);
        doAnswer(invocation -> {
            completed.countDown();
            return null;
        }).when(handler).execute(payload);

        scheduler(registry, repository, executor(1, 1)).pollDueJobs();

        assertTrue(completed.await(1, TimeUnit.SECONDS));
        verify(handler).execute(payload);
        verify(repository).save(job);
        assertEquals(EJobStatus.SUCCEEDED, job.getStatus());
    }

    @Test
    void pollDueJobs_marksFailedJobsAndContinuesWithTheNextJob() throws Exception {
        JobRepository repository = mock(JobRepository.class);
        JobHandlerRegistry registry = mock(JobHandlerRegistry.class);
        JobHandler handler = mock(JobHandler.class);
        JsonNode failedPayload = objectMapper.readTree("{\"id\":1}");
        Job failedJob = job(1L, failedPayload);
        Job succeedingJob = job(2L, objectMapper.readTree("{\"id\":2}"));
        CountDownLatch completed = new CountDownLatch(2);

        when(repository.findReadyJobs(any(), any(), any())).thenReturn(List.of(failedJob, succeedingJob));
        when(repository.claimJob(any(), any(), any(), any())).thenReturn(1);
        when(repository.findById(1L)).thenReturn(Optional.of(failedJob));
        when(repository.findById(2L)).thenReturn(Optional.of(succeedingJob));
        when(registry.getHandler(EJobType.LOG)).thenReturn(handler);
        doAnswer(invocation -> {
            try {
                if (failedPayload.equals(invocation.getArgument(0))) {
                    throw new IllegalStateException("handler failed");
                }
                return null;
            } finally {
                completed.countDown();
            }
        }).when(handler).execute(any(JsonNode.class));

        scheduler(registry, repository, executor(2, 2)).pollDueJobs();

        assertTrue(completed.await(1, TimeUnit.SECONDS));
        verify(repository).save(failedJob);
        verify(repository).save(succeedingJob);
        assertEquals(EJobStatus.FAILED, failedJob.getStatus());
        assertEquals(EJobStatus.SUCCEEDED, succeedingJob.getStatus());
    }

    @Test
    void pollDueJobs_runsFiveJobsConcurrently() throws Exception {
        JobRepository repository = mock(JobRepository.class);
        JobHandlerRegistry registry = mock(JobHandlerRegistry.class);
        JobHandler handler = mock(JobHandler.class);
        List<Job> jobs = List.of(
                job(1L, objectMapper.readTree("{\"id\":1}")),
                job(2L, objectMapper.readTree("{\"id\":2}")),
                job(3L, objectMapper.readTree("{\"id\":3}")),
                job(4L, objectMapper.readTree("{\"id\":4}")),
                job(5L, objectMapper.readTree("{\"id\":5}"))
        );
        Map<Long, Job> jobsById = jobs.stream().collect(java.util.stream.Collectors.toMap(Job::getId, job -> job));
        CountDownLatch allStarted = new CountDownLatch(5);
        CountDownLatch releaseHandlers = new CountDownLatch(1);
        CountDownLatch allCompleted = new CountDownLatch(5);

        when(repository.findReadyJobs(any(), any(), any())).thenReturn(jobs);
        when(repository.claimJob(any(), any(), any(), any())).thenReturn(1);
        when(repository.findById(any())).thenAnswer(invocation -> Optional.of(jobsById.get(invocation.getArgument(0))));
        when(registry.getHandler(EJobType.LOG)).thenReturn(handler);
        doAnswer(invocation -> {
            allStarted.countDown();
            try {
                releaseHandlers.await();
                return null;
            } finally {
                allCompleted.countDown();
            }
        }).when(handler).execute(any(JsonNode.class));

        scheduler(registry, repository, executor(5, 5)).pollDueJobs();

        assertTrue(allStarted.await(1, TimeUnit.SECONDS));
        releaseHandlers.countDown();
        assertTrue(allCompleted.await(1, TimeUnit.SECONDS));
        verify(handler, times(5)).execute(any(JsonNode.class));
    }

    private JobScheduler scheduler(
            JobHandlerRegistry registry,
            JobRepository repository,
            ThreadPoolExecutor executor
    ) {
        return new JobScheduler(registry, repository, 50, executor);
    }

    private ThreadPoolExecutor executor(int workerCount, int queueCapacity) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity)
        );
        executors.add(executor);
        return executor;
    }

    private Job job(Long id, JsonNode payload) {
        Job job = new Job();
        job.setId(id);
        job.setType(EJobType.LOG);
        job.setStatus(EJobStatus.PENDING);
        job.setPayload(payload);
        job.setScheduledAt(Instant.now());
        return job;
    }
}
