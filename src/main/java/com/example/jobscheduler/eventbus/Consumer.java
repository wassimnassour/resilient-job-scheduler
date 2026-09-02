package com.example.jobscheduler.eventbus;

@FunctionalInterface
public interface Consumer<T> {
    void accept(T event);
}
