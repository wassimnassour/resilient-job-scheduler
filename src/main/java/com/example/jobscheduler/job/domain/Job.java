package com.example.jobscheduler.job.domain;

import com.example.jobscheduler.job.enums.EJobType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.jobscheduler.job.enums.EJobStatus;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

@Data
@Entity
@Table(
        name = "job",
        indexes = {
                @Index(name = "idx_status_scheduleAt", columnList = "status,scheduledAt")
        }
)
public class Job {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private EJobType type;

    @Enumerated(EnumType.STRING)
    private EJobStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "update_at", nullable = false)
    private Instant updatedAt;

    private Instant scheduledAt;


    @PrePersist()
    private void beforeCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    private void beforeUpdate() {
        this.updatedAt = Instant.now();
    }

}
