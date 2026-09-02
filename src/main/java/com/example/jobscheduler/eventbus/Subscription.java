package com.example.jobscheduler.eventbus;

@FunctionalInterface
public interface Subscription {
    void unSubscribe();
}
