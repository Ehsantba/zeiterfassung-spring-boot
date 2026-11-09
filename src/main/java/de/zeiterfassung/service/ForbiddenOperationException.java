package de.zeiterfassung.service;

public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
