package com.example.jobscheduler.job.scheduler;

import com.example.jobscheduler.job.domain.Job;
import com.example.jobscheduler.job.enums.EJobStatus;
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
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

@Component
public class JobScheduler {

    private final JobHandlerRegistry jobHandlerRegistry;
    private final JobRepository jobRepository;
    private final int batchSize;
    private final ThreadPoolExecutor jobExecutor;


    private static final Logger logger = LoggerFactory.getLogger(JobScheduler.class);

    public JobScheduler(
            JobHandlerRegistry jobHandlerRegistry,
            JobRepository jobRepository,
            @Value("${scheduler.batch-size:50}") int batchSize,
            @Qualifier("jobExecutor") ThreadPoolExecutor jobExecutor
    ) {
        this.jobHandlerRegistry = jobHandlerRegistry;
        this.jobRepository = jobRepository;
        this.batchSize = batchSize;
        this.jobExecutor = jobExecutor;
    }


    @Scheduled(fixedDelayString = "${scheduler.poll-interval-ms:5000}")
    public void pollDueJobs() {

        int freeSlots = availableSlots();
        if (freeSlots == 0) {
            return;
        }

        int jobsToFetch = Math.min(batchSize, freeSlots);
        List<Job> listOfJobs = jobRepository.findReadyJobs(
                EJobStatus.PENDING,
                Instant.now(),
                PageRequest.of(0, jobsToFetch)
        );

        if (!listOfJobs.isEmpty()) {
            logger.info("Claiming up to {} due jobs", listOfJobs.size());
        }

        for (Job job : listOfJobs) {
            boolean claimed = jobRepository.claimJob(
                    EJobStatus.RUNNING,
                    EJobStatus.PENDING,
                    job.getId(),
                    Instant.now()
            ) == 1;

            if (claimed) {
                try {
                    jobExecutor.submit(() -> processJob(job.getId()));
                } catch (RejectedExecutionException exception) {
                    jobRepository.releaseClaim(
                            job.getId(),
                            EJobStatus.RUNNING,
                            EJobStatus.PENDING,
                            Instant.now()
                    );
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
                        + jobExecutor.getQueue().remainingCapacity()
        );
    }

    public void processJob(Long jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new IllegalStateException("Can't find this Job"));

        logger.info("Processing job {} of type {}", job.getId(), job.getType());
        try {
            JobHandler handler = jobHandlerRegistry.getHandler(job.getType());
            handler.execute(job.getPayload());
            job.setStatus(EJobStatus.SUCCEEDED);
            jobRepository.save(job);
        } catch (Exception e) {
            job.setStatus(EJobStatus.FAILED);
            jobRepository.save(job);
            logger.error("Failed to execute job {} of type {}", job.getId(), job.getType(), e);
        }
    }
}
