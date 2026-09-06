package com.example.jobscheduler.job.repository;

import com.example.jobscheduler.job.domain.Job;
import com.example.jobscheduler.job.enums.EJobStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {
    @Query(""" 
               Select j from Job j
                 where j.status = :status
                 AND j.scheduledAt <= :now
                 order by j.scheduledAt asc
            """)
    List<Job> findReadyJobs(
            @Param("status") EJobStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );


    @Modifying
    @Transactional
    @Query("""
                   update Job j
                   set j.status = :newStatus, j.updatedAt = :now
                   where j.id = :jobId and j.status = :pendingStatus
            """)
    int claimJob(
            @Param("newStatus") EJobStatus newStatus,
            @Param("pendingStatus") EJobStatus pendingStatus,
            @Param("jobId") Long jobId,
            @Param("now") Instant now
    );

    @Modifying
    @Transactional
    @Query("""
                   update Job j
                   set j.status = :pendingStatus, j.updatedAt = :now
                   where j.id = :jobId and j.status = :runningStatus
            """)
    int releaseClaim(
            @Param("jobId") Long jobId,
            @Param("runningStatus") EJobStatus runningStatus,
            @Param("pendingStatus") EJobStatus pendingStatus,
            @Param("now") Instant now
    );

}
