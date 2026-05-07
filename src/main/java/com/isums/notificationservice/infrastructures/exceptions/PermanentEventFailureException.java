package com.isums.notificationservice.infrastructures.exceptions;

public class PermanentEventFailureException extends RuntimeException {

    public PermanentEventFailureException(String message, Throwable cause) {
        super(message, cause);
    }

    public PermanentEventFailureException(String message) {
        super(message);
    }
}
