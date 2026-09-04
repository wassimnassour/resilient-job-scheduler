package com.example.jobscheduler.job.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JobHandlerRegistryTest {

    @Test
    void getHandler_ShouldReturnHandler_whenJobTypeIsRegister() {
        String JobType = "EMAIL";
        JobHandler handler = mock(JobHandler.class);
        when(handler.getType()).thenReturn(JobType);

        JobHandlerRegistry registry = new JobHandlerRegistry(List.of(handler));
        JobHandler result = registry.getHandler("EMAIL");

        assertSame(result, handler);
    }

    @Test
    void getHandler_shouldReturnError_whenJobTypeIsDuplicated() {
        String JobType = "EMAIL";
//        Arrange
        JobHandler firstHandler = mock(JobHandler.class);
        when(firstHandler.getType()).thenReturn(JobType);

        JobHandler secondHandler = mock(JobHandler.class);
        when(secondHandler.getType()).thenReturn(JobType);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> new JobHandlerRegistry(List.of(firstHandler, secondHandler)));

        assertEquals("Multiple handlers registered for job type: EMAIL", exception.getMessage());
    }

    @Test
    void getHandler_shouldReturnError_whenTryToGetHandlerNotExists() {
        String type = "LOG";
        String typeToCheckOn = "EMAIL";

        JobHandler handler = mock(JobHandler.class);
        when(handler.getType()).thenReturn(type);

        JobHandlerRegistry registry = new JobHandlerRegistry(List.of(handler));

        IllegalArgumentException exaption = assertThrows(IllegalArgumentException.class, () -> registry.getHandler(typeToCheckOn));

        assertEquals("This job Type is not Supported", exaption.getMessage());
        ;

    }

}
