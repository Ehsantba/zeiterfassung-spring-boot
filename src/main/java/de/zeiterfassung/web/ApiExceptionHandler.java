package de.zeiterfassung.web;

import de.zeiterfassung.service.BusinessRuleException;
import de.zeiterfassung.service.ConflictException;
import de.zeiterfassung.service.ForbiddenOperationException;
import de.zeiterfassung.service.NotFoundException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Globale Fehlerbehandlung: einheitliche Antworten nach RFC 9457 (ProblemDetail), keine internen Details. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail businessRule(BusinessRuleException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    ProblemDetail forbidden(ForbiddenOperationException ex) {
        return problem(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler({ConflictException.class})
    ProblemDetail conflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail optimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT,
                "Die Daten wurden zwischenzeitlich von jemand anderem geändert. Bitte neu laden und erneut versuchen.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException ex) {
        log.warn("Datenbank-Constraint verletzt", ex);
        return problem(HttpStatus.CONFLICT, "Die Daten verletzen eine Datenbank-Regel.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Ungültige Eingabe." : message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail unreadable(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Die Anfrage konnte nicht gelesen werden (Format prüfen).");
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
