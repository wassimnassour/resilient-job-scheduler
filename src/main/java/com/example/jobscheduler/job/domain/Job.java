package com.example.jobscheduler.job.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Job {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

}
