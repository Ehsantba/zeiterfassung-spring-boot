package de.zeiterfassung.service;

/** Fachliche Regel verletzt (wird als HTTP 422 zurückgegeben). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
