package com.example.jobscheduler.job.scheduler;

import com.example.jobscheduler.eventbus.EventBus;
import com.example.jobscheduler.job.domain.Job;
import com.example.jobscheduler.job.enums.EJobStatus;
import com.example.jobscheduler.job.event.JobExhaustedEvent;
import com.example.jobscheduler.job.event.JobRetryScheduledEvent;
import com.example.jobscheduler.job.event.JobSucceededEvent;
import com.example.jobscheduler.job.handler.JobHandler;
import com.example.jobscheduler.job.handler.JobHandlerRegistry;
import com.example.jobscheduler.job.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

@Component
public class JobScheduler {

    private final JobHandlerRegistry jobHandlerRegistry;
    private final JobRepository jobRepository;
    private final int batchSize;
    private final ThreadPoolExecutor jobExecutor;
    private final int maxAttempts;
    private final long retryBaseDelaySeconds;
    private final EventBus eventBus;
    private final ThreadPoolExecutor handlerExecutor;
    private final long jobTimeoutSeconds;

    private static final Logger logger = LoggerFactory.getLogger(JobScheduler.class);

    public JobScheduler(
            JobHandlerRegistry jobHandlerRegistry,
            JobRepository jobRepository,
            @Value("${scheduler.batch-size:50}") int batchSize,
            @Value("${scheduler.max-attempts:5}") int maxAttempts,
            @Value("${scheduler.retry-base-delay-seconds:5}") long retryBaseDelaySeconds,
            @Value("${scheduler.job-timeout-seconds:5}") long jobTimeoutSeconds,
            @Qualifier("jobExecutor") ThreadPoolExecutor jobExecutor,
            EventBus eventBus,
            @Qualifier("handlerExecutor") ThreadPoolExecutor handlerExecutor
    ) {
        this.jobHandlerRegistry = jobHandlerRegistry;
        this.jobRepository = jobRepository;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.retryBaseDelaySeconds = retryBaseDelaySeconds;
        this.jobExecutor = jobExecutor;
        this.eventBus = eventBus;
        this.handlerExecutor = handlerExecutor;
        this.jobTimeoutSeconds = jobTimeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${scheduler.poll-interval-ms:5000}")
    public void pollDueJobs() {
        logger.info("PollDueJobs Running");

        int freeSlots = availableSlots();
        if (freeSlots == 0) {
            return;
        }

        int jobsToFetch = Math.min(batchSize, freeSlots);
        List<Job> listOfJobs = jobRepository.findReadyJobs(
                EJobStatus.PENDING,
                Instant.now(),
                PageRequest.of(0, jobsToFetch));

        if (!listOfJobs.isEmpty()) {
            logger.info("Claiming up to {} due jobs", listOfJobs.size());
        }

        for (Job job : listOfJobs) {
            boolean claimed = jobRepository.claimJob(
                    EJobStatus.RUNNING,
                    EJobStatus.PENDING,
                    job.getId(),
                    Instant.now()) == 1;

            if (claimed) {
                try {
                    jobExecutor.submit(() -> {
                        processJob(job.getId());
                    });
                } catch (RejectedExecutionException exception) {
                    jobRepository.releaseClaim(
                            job.getId(),
                            EJobStatus.RUNNING,
                            EJobStatus.PENDING,
                            Instant.now());
                    logger.warn("Executor was full; released job {} back to PENDING", job.getId(), exception);
                }
            }
        }

    }

    private int availableSlots() {
        return Math.max(
                0,
                jobExecutor.getMaximumPoolSize()
                        - jobExecutor.getActiveCount()
                        + jobExecutor.getQueue().remainingCapacity());
    }

    public void processJob(Long jobId) {
//        Thread.sleep(2000);
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new IllegalStateException("Can't find this Job"));

        logger.info("Processing job {} of type {}", job.getId(), job.getType());

        JobHandler handler = jobHandlerRegistry.getHandler(job.getType());

        Future<?> future;
        try {
            future = handlerExecutor.submit(() -> {
                try {
                    handler.execute(job.getPayload());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            handleFailure(exception, job);
            return;
        }

        try {
            future.get(jobTimeoutSeconds, TimeUnit.SECONDS);
            job.setStatus(EJobStatus.SUCCEEDED);
            jobRepository.save(job);
            eventBus.publish(JobSucceededEvent.now(job.getId()));
        } catch (TimeoutException timeoutException) {
            future.cancel(true);
            handleFailure(timeoutException, job);
        } catch (ExecutionException e) {
            handleFailure(e.getCause(), job);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for job {} to finish", job.getId(), e);
        }
    }

    private void handleFailure(Throwable e, Job job) {
        job.setAttemptsCount(job.getAttemptsCount() + 1);
        logger.error("Failed to execute job {} of type {}", job.getId(), job.getType(), e);

        if (job.getAttemptsCount() < maxAttempts) {
            job.setStatus(EJobStatus.PENDING);
            long delaySeconds = retryBaseDelaySeconds * (1L << (job.getAttemptsCount() - 1));
            job.setScheduledAt(Instant.now().plusSeconds(delaySeconds));
            jobRepository.save(job);
            eventBus.publish(JobRetryScheduledEvent.now(job.getId()));

        } else {
            job.setStatus(EJobStatus.EXHAUSTED);
            jobRepository.save(job);
            eventBus.publish(JobExhaustedEvent.now(job.getId()));

        }
    }
}
