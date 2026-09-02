package com.example.jobscheduler.eventbus;

public interface EventBus {

    <T extends Event> Subscription subscribe(Class<T> eventType, Consumer<T> listener);

    void publish(Event event);

}
