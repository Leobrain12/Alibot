package com.alibot.api;

import com.alibot.service.exception.ForbiddenException;
import com.alibot.service.exception.InvalidTransitionException;
import com.alibot.service.exception.NotFoundException;
import com.alibot.service.exception.StaleOrderStateException;
import com.alibot.service.exception.ValidationException;
import java.time.format.DateTimeParseException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorBody> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorBody> forbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler({ValidationException.class, InvalidTransitionException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorBody> badRequest(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler(StaleOrderStateException.class)
    public ResponseEntity<ErrorBody> conflict(StaleOrderStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody(e.getMessage()));
    }

    /** ТЗ п.112 — параллельное изменение заказа (см. Order#version). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorBody> staleVersion(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorBody("Заявка уже изменена администратором. Обновите список заказов."));
    }

    /** Битый UUID/enum в @PathVariable или @RequestParam (например GET /api/v1/orders/not-a-uuid) —
     *  Spring бросает это ДО вызова контроллера, класс не наследуется от IllegalArgumentException,
     *  поэтому без отдельного handler'а это раньше улетало как raw 500 вместо чистого 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorBody> badArgument(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorBody("Некорректное значение параметра «%s»".formatted(e.getName())));
    }

    /** Instant.parse(...) на невалидной строке (StatsController/MasterController/ExportController
     *  принимают from/to как ISO-instant) — DateTimeParseException не наследует IllegalArgumentException,
     *  тот же класс проблемы, что и выше. */
    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ErrorBody> badDate(DateTimeParseException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorBody("Некорректный формат даты/времени: " + e.getParsedString()));
    }

    public record ErrorBody(String message) {
    }
}
