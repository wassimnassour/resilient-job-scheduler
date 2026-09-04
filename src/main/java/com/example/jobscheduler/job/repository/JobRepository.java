package com.example.jobscheduler.job.repository;

import com.example.jobscheduler.job.domain.Job;
import com.example.jobscheduler.job.enums.EJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {
    @Query(""" 
               Select j from Job j
                 where j.status = :status
                 AND j.scheduledAt <= :now
            
            """)
    List<Job> findReadyJobs(
            @Param("status") EJobStatus status,
            @Param("now") Instant now
    );


}
