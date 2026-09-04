package com.example.jobscheduler.job.scheduler;

import com.example.jobscheduler.job.domain.Job;
import com.example.jobscheduler.job.enums.EJobStatus;
import com.example.jobscheduler.job.handler.JobHandler;
import com.example.jobscheduler.job.handler.JobHandlerRegistry;
import com.example.jobscheduler.job.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

@Component
public class JobScheduler {

    private final JobHandlerRegistry jobHandlerRegistry;
    private final JobRepository jobRepository;
    private final int batchSize;

    private static final Logger logger = LoggerFactory.getLogger(JobScheduler.class);

    public JobScheduler(
            JobHandlerRegistry jobHandlerRegistry,
            JobRepository jobRepository,
            @Value("${scheduler.batch-size:50}") int batchSize
    ) {
        this.jobHandlerRegistry = jobHandlerRegistry;
        this.jobRepository = jobRepository;
        this.batchSize = batchSize;
    }


    @Scheduled(fixedDelayString = "${scheduler.poll-interval-ms:5000}")
    public void pollDueJobs() {
        List<Job> listOfJobs = jobRepository.findReadyJobs(
                EJobStatus.PENDING,
                Instant.now(),
                PageRequest.of(0, batchSize)
        );

        for (Job job : listOfJobs) {
            processJob(job);
        }

        if (!listOfJobs.isEmpty()) {
            logger.info("Processed {} due jobs", listOfJobs.size());
        }
    }


    public void processJob(Job job) {

        logger.info("Processing job {} of type {}", job.getId(), job.getType());
        job.setStatus(EJobStatus.RUNNING);
        jobRepository.save(job);
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
