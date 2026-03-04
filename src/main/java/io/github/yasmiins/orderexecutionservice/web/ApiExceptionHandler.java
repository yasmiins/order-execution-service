package io.github.yasmiins.orderexecutionservice.web;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import io.github.yasmiins.orderexecutionservice.service.IdempotencyConflictException;
import io.github.yasmiins.orderexecutionservice.service.OrderMetrics;
import io.github.yasmiins.orderexecutionservice.service.OrderNotFoundException;
import io.github.yasmiins.orderexecutionservice.service.OrderStateException;
import io.github.yasmiins.orderexecutionservice.service.OrderValidationException;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String LIFECYCLE_LOG_TEMPLATE =
        "event={} orderId={} symbol={} fromStatus={} toStatus={} filledQuantity={} quantity={} price={} idempotencyKey={}";
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final OrderMetrics orderMetrics;

    public ApiExceptionHandler(OrderMetrics orderMetrics) {
        this.orderMetrics = orderMetrics;
    }

    @ExceptionHandler(OrderValidationException.class)
    public ResponseEntity<ApiError> handleOrderValidation(OrderValidationException ex, HttpServletRequest request) {
        if (isCreateOrderRequest(request)) {
            orderMetrics.incrementRejectedValidation();
            log.warn(
                LIFECYCLE_LOG_TEMPLATE,
                "order_rejected_validation",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                resolveIdempotencyKey(request)
            );
        }
        ApiError error = new ApiError(ex.getMessage(), Collections.emptyMap());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        if (isCreateOrderRequest(request)) {
            orderMetrics.incrementRejectedValidation();
            log.warn(
                LIFECYCLE_LOG_TEMPLATE,
                "order_rejected_validation",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                resolveIdempotencyKey(request)
            );
        }
        Map<String, String> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            String message = error.getDefaultMessage();
            details.put(error.getField(), message);
        });
        ApiError error = new ApiError("Validation failed", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
        MethodArgumentTypeMismatchException ex,
        HttpServletRequest request
    ) {
        if (isCreateOrderRequest(request)) {
            orderMetrics.incrementRejectedValidation();
            log.warn(
                LIFECYCLE_LOG_TEMPLATE,
                "order_rejected_validation",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                resolveIdempotencyKey(request)
            );
        }
        String name = ex.getName();
        Object value = ex.getValue();
        String message = value == null
            ? "Invalid value for parameter: " + name
            : "Invalid value for parameter '" + name + "': " + value;
        ApiError error = new ApiError(message, Collections.emptyMap());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMessageNotReadable(
        HttpMessageNotReadableException ex,
        HttpServletRequest request
    ) {
        if (isCreateOrderRequest(request)) {
            orderMetrics.incrementRejectedValidation();
            log.warn(
                LIFECYCLE_LOG_TEMPLATE,
                "order_rejected_validation",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                resolveIdempotencyKey(request)
            );
        }
        ApiError error = new ApiError("Malformed request body", Collections.emptyMap());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ApiError error = new ApiError("Order was updated by another request. Please retry.", Collections.emptyMap());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotencyConflict(IdempotencyConflictException ex) {
        ApiError error = new ApiError(ex.getMessage(), Collections.emptyMap());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiError> handleOrderNotFound(OrderNotFoundException ex) {
        ApiError error = new ApiError(ex.getMessage(), Collections.emptyMap());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(OrderStateException.class)
    public ResponseEntity<ApiError> handleOrderState(OrderStateException ex) {
        ApiError error = new ApiError(ex.getMessage(), Collections.emptyMap());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    private boolean isCreateOrderRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return "/orders".equals(uri) || "/orders/".equals(uri);
    }

    private String resolveIdempotencyKey(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader("Idempotency-Key");
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
