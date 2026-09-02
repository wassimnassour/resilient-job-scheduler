# EventBus 🚀

A lightweight, high-performance, in-memory **Asynchronous Event Bus** implementation for Spring Boot applications powered by Java 21.

---

## 📑 Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Architecture & Design](#architecture--design)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [API Endpoints](#api-endpoints)
- [Usage & Code Examples](#usage--code-examples)
  - [1. Defining an Event](#1-defining-an-event)
  - [2. Subscribing Listeners](#2-subscribing-listeners)
  - [3. Publishing Events](#3-publishing-events)
  - [4. Polymorphic / Fanout Auditing](#4-polymorphic--fanout-auditing)
- [Project Structure](#project-structure)

---

## 🎯 Overview

This project provides a decoupled, non-blocking event-driven architecture within a Spring Boot application. Domain services (such as **Orders** and **Clients**) publish domain events to the `EventBus`, where a background worker pool delivers them asynchronously to all registered listeners.

---

## ✨ Key Features

- **⚡ Asynchronous & Non-Blocking**: Event producers publish events immediately without blocking on listener execution.
- **🔒 Thread-Safe & Concurrent**: Uses `ConcurrentHashMap` and `CopyOnWriteArrayList` for lock-free listener registration and dynamic unsubscription.
- **🔀 Polymorphic Event Dispatching**: Supports class-hierarchy matching with Java 21 `sealed interfaces`—allowing subscribers to listen to specific events or all events under a parent interface (like a topic/fanout exchange).
- **🛡️ Fault Isolation & Resilience**: Individual subscriber failures are caught and logged without affecting other listeners or killing consumer worker threads.
- **🧹 Graceful Lifecycle Management**: Implements `AutoCloseable` to ensure executor threads and event queues are cleanly drained on application shutdown.

---

## 🏗️ Architecture & Design

```mermaid
flowchart TD
    subgraph Producers ["Event Producers (REST / Services)"]
        OrderService["OrderService"]
        ClientService["ClientService"]
    end

    subgraph EventBusEngine ["Async Event Bus Engine"]
        Queue["LinkedBlockingQueue<Event>"]
        Workers["Worker Pool (4 Threads)"]
        Registry["Subscription Registry<br/>(ConcurrentHashMap)"]
    end

    subgraph Consumers ["Event Listeners"]
        CrudListener["OrderCrudListener<br/>(OrderCreatedEvent, OrderCanceledEvent)"]
        AuditListener["OrderAuditListener<br/>(OrderEvent - Fanout/Polymorphic)"]
    end

    OrderService -->|publish(event)| Queue
    ClientService -->|publish(event)| Queue

    Queue --> Workers
    Workers -->|deliver(event)| Registry
    Registry -->|dispatch| CrudListener
    Registry -->|dispatch| AuditListener
```

---

## 🛠️ Tech Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3 / 4
- **Persistence**: Spring Data JPA & H2 Database
- **Utilities**: Lombok, SLF4J

---

## 🚀 Getting Started

### Prerequisites

- **JDK 21** or higher installed
- **Maven 3.8+** (or use the included `./mvnw` wrapper)

### Run Locally

1. **Clone the repository:**
   ```bash
   git clone https://github.com/wassimnassour/Event-bus.git
   cd Event-bus
   ```

2. **Build and start the application:**
   ```bash
   ./mvnw spring-boot:run
   ```

3. **Access H2 Database Console:**
   - URL: `http://localhost:8080/h2-console`
   - JDBC URL: `jdbc:h2:mem:testdb`
   - User: `sa`
   - Password: *(empty)*

---

## 📡 API Endpoints

### 🛒 Orders API

| Method | Endpoint | Description | Sample Payload |
| :--- | :--- | :--- | :--- |
| `GET` | `/orders` | Fetch all orders | — |
| `POST` | `/orders` | Create an order & publish `OrderCreatedEvent` | `{"item": "Laptop", "quantity": 2}` |
| `POST` | `/orders/cancel` | Cancel an order & publish `OrderCanceledEvent` | `{"orderId": 1}` |

#### Example: Create Order
```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"item": "Mechanical Keyboard", "quantity": 1}'
```

#### Example: Cancel Order
```bash
curl -X POST http://localhost:8080/orders/cancel \
  -H "Content-Type: application/json" \
  -d '{"orderId": 1}'
```

---

### 👤 Clients API

| Method | Endpoint | Description | Sample Payload |
| :--- | :--- | :--- | :--- |
| `GET` | `/clients` | Fetch all clients | — |
| `POST` | `/clients` | Create a client & publish `ClientCreatedEvent` | `{"name": "Alice", "email": "alice@example.com"}` |

#### Example: Create Client
```bash
curl -X POST http://localhost:8080/clients \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice Doe", "email": "alice@example.com"}'
```

---

## 💻 Usage & Code Examples

### 1. Defining an Event

Implement the core `Event` interface:

```java
package com.example.eventBus.clients.event;

import com.example.eventBus.config.Event;
import java.time.Instant;

public record ClientCreatedEvent(Long clientId, String name, String email, Instant occurredAt) implements Event {
    public static ClientCreatedEvent now(Long clientId, String name, String email) {
        return new ClientCreatedEvent(clientId, name, email, Instant.now());
    }
}
```

### 2. Subscribing Listeners

Register callbacks using method references or lambdas:

```java
@Configuration
public class EventBusSubscriptions {

    @PostConstruct
    public void register(EventBus eventBus, OrderCrudListener crudListener) {
        // Subscribe to a specific event
        Subscription sub = eventBus.subscribe(OrderCreatedEvent.class, crudListener::createOrder);

        // Optionally unsubscribe later:
        // sub.unSubscribe();
    }
}
```

### 3. Publishing Events

Inject the `EventBus` into your service and publish:

```java
@Service
public class OrderServiceImpl implements OrderService {
    private final EventBus eventBus;

    public Order createOrder(CreateOrderCommand command) {
        Order order = orderRepository.save(new Order(...));
        
        // Fire-and-forget asynchronous publishing
        eventBus.publish(OrderCreatedEvent.now(order.getId(), order.getItem(), order.getQuantity()));
        return order;
    }
}
```

### 4. Polymorphic / Fanout Auditing

Use Java 21 `sealed interfaces` to capture all domain events under a parent type:

```java
public sealed interface OrderEvent extends Event permits OrderCreatedEvent, OrderCanceledEvent {}

// Subscribing to the parent type captures both OrderCreatedEvent and OrderCanceledEvent
eventBus.subscribe(OrderEvent.class, orderAuditListener::onAnyActionOnOrder);
```

---

## 📁 Project Structure

```
src/main/java/com/example/eventBus/
├── EventBusApplication.java
├── config/
│   ├── Consumer.java               # Functional interface for listener callbacks
│   ├── Event.java                  # Base Event interface with timestamp
│   ├── EventBusConfig.java         # Spring configuration & thread pool setup
│   └── Subscription.java           # Unsubscribe handle
├── eventBus/
│   ├── EventBus.java               # Core EventBus contract
│   ├── AsyncEventBus.java          # Asynchronous worker-backed EventBus implementation
│   └── EventBusSubscriptions.java  # Listener registration registry
├── orders/
│   ├── command/                    # CreateOrderCommand, OrderCanceledCommand
│   ├── controller/                 # Orders REST controller
│   ├── event/                      # OrderEvent, OrderCreatedEvent, OrderCanceledEvent
│   ├── listener/                   # OrderCrudListener, OrderAuditListener
│   ├── model/                      # Order JPA entity
│   ├── repository/                 # OrderRepository
│   └── service/                    # OrderService, OrderServiceImpl
└── clients/
    ├── command/                    # CreateClientCommand
    ├── controller/                 # ClientController
    ├── event/                      # ClientCreatedEvent
    ├── model/                      # Client JPA entity
    ├── repository/                 # ClientRepository
    └── service/                    # ClientService, ClientServiceImpl
```

---

## 📄 License

This project is licensed under the MIT License.
