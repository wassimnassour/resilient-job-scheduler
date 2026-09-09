package com.example.jobscheduler.job.scheduler;

import com.example.jobscheduler.eventbus.EventBus;
import com.example.jobscheduler.job.domain.Job;
import com.example.jobscheduler.job.enums.EJobStatus;
import com.example.jobscheduler.job.enums.EJobType;
import com.example.jobscheduler.job.event.JobSucceededEvent;
import com.example.jobscheduler.job.handler.JobHandler;
import com.example.jobscheduler.job.handler.JobHandlerRegistry;
import com.example.jobscheduler.job.repository.JobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
        CountDownLatch jobsSaved = new CountDownLatch(2);

        when(repository.findReadyJobs(any(), any(), any())).thenReturn(List.of(failedJob, succeedingJob));
        when(repository.claimJob(any(), any(), any(), any())).thenReturn(1);
        when(repository.findById(1L)).thenReturn(Optional.of(failedJob));
        when(repository.findById(2L)).thenReturn(Optional.of(succeedingJob));
        when(registry.getHandler(EJobType.LOG)).thenReturn(handler);
        doAnswer(invocation -> {
            jobsSaved.countDown();
            return invocation.getArgument(0);
        }).when(repository).save(any(Job.class));
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

        scheduler(registry, repository, executor(2, 2), 5).pollDueJobs();

        assertTrue(completed.await(1, TimeUnit.SECONDS));
        assertTrue(jobsSaved.await(1, TimeUnit.SECONDS));
        verify(repository).save(failedJob);
        verify(repository).save(succeedingJob);
        assertEquals(EJobStatus.PENDING, failedJob.getStatus());
        assertEquals(1, failedJob.getAttemptsCount());
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

    @Test
    public void pollDueJobs_shouldMakeJobExhausted_whenJobKeepsFailing() throws Exception {
        JobRepository repository = mock(JobRepository.class);
        JobHandlerRegistry registry = mock(JobHandlerRegistry.class);

        JsonNode jsonPayload = objectMapper.readTree("{\"id\":1}");

        Job pendingJob = job(1L, jsonPayload);
        pendingJob.setAttemptsCount(4);

        JobHandler handler = mock(JobHandler.class);

        when(registry.getHandler(EJobType.LOG))
                .thenReturn(handler);

        doThrow(new IllegalStateException("Job failed"))
                .when(handler)
                .execute(jsonPayload);

        when(repository.findReadyJobs(any(), any(), any()))
                .thenReturn(List.of(pendingJob));
        when(repository.claimJob(any(), any(), any(), any()))
                .thenReturn(1);
        when(repository.findById(1L))
                .thenReturn(Optional.of(pendingJob));

        when(repository.save(any(Job.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        scheduler(
                registry,
                repository,
                executor(5, 10),
                5
        ).pollDueJobs();


        await()
                .atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertEquals(EJobStatus.EXHAUSTED, pendingJob.getStatus());
                    assertEquals(5, pendingJob.getAttemptsCount());
                });
    }

    @Test
    void processJob_schedulesThirdFailedAttemptWithTwentySecondDelay() throws Exception {
        JobRepository repository = mock(JobRepository.class);
        JobHandlerRegistry registry = mock(JobHandlerRegistry.class);
        JobHandler handler = mock(JobHandler.class);
        Job job = job(1L, objectMapper.readTree("{\"id\":1}"));
        job.setAttemptsCount(2);
        Instant beforeExecution = Instant.now();

        when(repository.findById(1L)).thenReturn(Optional.of(job));
        when(registry.getHandler(EJobType.LOG)).thenReturn(handler);
        doThrow(new IllegalStateException("Job failed")).when(handler).execute(job.getPayload());

        scheduler(registry, repository, executor(1, 1), 5).processJob(1L);

        assertEquals(3, job.getAttemptsCount());
        assertEquals(EJobStatus.PENDING, job.getStatus());
        assertFalse(job.getScheduledAt().isBefore(beforeExecution.plusSeconds(20)));
        assertFalse(job.getScheduledAt().isAfter(Instant.now().plusSeconds(20)));
    }

    @Test
    void pollDueJobs_publishesSucceededEvent_whenJobSucceeds() throws Exception {
        EventBus eventBus = mock(EventBus.class);
        JobRepository jobRepository = mock(JobRepository.class);
        JobHandlerRegistry jobHandlerRegistry = mock(JobHandlerRegistry.class);
        JobScheduler scheduler = scheduler(
                jobHandlerRegistry,
                jobRepository,
                executor(1, 1),
                4,
                eventBus,
                executor(1, 1)
        );
        Job job = job(1L, objectMapper.readTree("{\"id\":1}"));

        JobHandler jobHandler = mock(JobHandler.class);

        when(jobRepository.findReadyJobs(any(), any(), any()))
                .thenReturn(List.of(job));
        when(jobRepository.claimJob(any(), any(), eq(1L), any()))
                .thenReturn(1);
        when(jobRepository.findById(1L))
                .thenReturn(Optional.of(job));

        when(jobHandlerRegistry.getHandler(EJobType.LOG))
                .thenReturn(jobHandler);

        CountDownLatch completed = new CountDownLatch(1);
        doAnswer(invocation -> {
            completed.countDown();
            return null;
        }).when(jobHandler).execute(any());

        scheduler.pollDueJobs();

        assertTrue(completed.await(2, TimeUnit.SECONDS));

        ArgumentCaptor<JobSucceededEvent> eventCaptor =
                ArgumentCaptor.forClass(JobSucceededEvent.class);
        verify(eventBus).publish(eventCaptor.capture());

        assertThat(eventCaptor.getValue().jobId()).isEqualTo(1L);
    }

    @Test
    void processJob_retriesWhenHandlerTimesOut() throws Exception {
        JobRepository repository = mock(JobRepository.class);
        JobHandlerRegistry registry = mock(JobHandlerRegistry.class);
        JobHandler handler = mock(JobHandler.class);
        Job job = job(1L, objectMapper.readTree("{\"id\":1}"));
        CountDownLatch interrupted = new CountDownLatch(1);

        when(repository.findById(job.getId())).thenReturn(Optional.of(job));
        when(registry.getHandler(EJobType.LOG)).thenReturn(handler);
        doAnswer(invocation -> {
            try {
                Thread.sleep(60_000);
                return null;
            } catch (InterruptedException exception) {
                interrupted.countDown();
                throw exception;
            }
        }).when(handler).execute(job.getPayload());

        JobScheduler scheduler = scheduler(
                registry,
                repository,
                executor(1, 1),
                5,
                mock(EventBus.class),
                executor(1, 1),
                1
        );

        scheduler.processJob(job.getId());

        assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        assertEquals(EJobStatus.PENDING, job.getStatus());
        assertEquals(1, job.getAttemptsCount());
    }


    private JobScheduler scheduler(
            JobHandlerRegistry registry,
            JobRepository repository,
            ThreadPoolExecutor executor
    ) {
        return scheduler(registry, repository, executor, 5);
    }

    private JobScheduler scheduler(
            JobHandlerRegistry registry,
            JobRepository repository,
            ThreadPoolExecutor executor,
            int maxAttempts
    ) {
        return scheduler(
                registry,
                repository,
                executor,
                maxAttempts,
                mock(EventBus.class),
                executor(executor.getMaximumPoolSize(), executor.getQueue().remainingCapacity()),
                5
        );
    }

    private JobScheduler scheduler(
            JobHandlerRegistry registry,
            JobRepository repository,
            ThreadPoolExecutor executor,
            int maxAttempts,
            EventBus eventBus
    ) {
        return scheduler(
                registry,
                repository,
                executor,
                maxAttempts,
                eventBus,
                executor(executor.getMaximumPoolSize(), executor.getQueue().remainingCapacity()),
                5
        );
    }

    private JobScheduler scheduler(
            JobHandlerRegistry registry,
            JobRepository repository,
            ThreadPoolExecutor executor,
            int maxAttempts,
            EventBus eventBus,
            ThreadPoolExecutor handlerExecutor
    ) {
        return scheduler(
                registry,
                repository,
                executor,
                maxAttempts,
                eventBus,
                handlerExecutor,
                5
        );
    }

    private JobScheduler scheduler(
            JobHandlerRegistry registry,
            JobRepository repository,
            ThreadPoolExecutor executor,
            int maxAttempts,
            EventBus eventBus,
            ThreadPoolExecutor handlerExecutor,
            long timeoutSeconds
    ) {
        return new JobScheduler(
                registry,
                repository,
                50,
                maxAttempts,
                5,
                timeoutSeconds,
                executor,
                eventBus,
                handlerExecutor
        );
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
