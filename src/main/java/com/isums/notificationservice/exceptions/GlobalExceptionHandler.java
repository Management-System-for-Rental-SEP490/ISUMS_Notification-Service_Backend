package com.isums.notificationservice.exceptions;

import com.isums.notificationservice.domains.dtos.ApiError;
import com.isums.notificationservice.domains.dtos.ApiResponse;
import com.isums.notificationservice.domains.dtos.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;


@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponses.fail(HttpStatus.NOT_FOUND, "Resource not found: " + ex.getResourcePath()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
                .body(ApiResponses.fail(status, ex.getReason() != null ? ex.getReason() : status.getReasonPhrase()));
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiResponse<Void>> handleErrorResponse(ErrorResponseException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
                .body(ApiResponses.fail(status, ex.getBody().getDetail() != null ? ex.getBody().getDetail() : status.getReasonPhrase()));
    }

    // Thrown when an SSE client disconnects mid-stream. Do not log as ERROR,
    // and do not wrap in a 500 response (the response is already closed).
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncClientGone(AsyncRequestNotUsableException ex) {
        log.debug("[SSE] Client disconnected: {}", ex.getMessage());
    }

    // Thrown when an SSE / long-polling endpoint times out waiting for a
    // DeferredResult. The response is already committed with
    // Content-Type: text/event-stream, so attempting to return an
    // ApiResponse JSON body triggers a secondary HttpMessageNotWritableException.
    // Return a void response — the client simply sees the stream close,
    // reconnects, and we stop spamming ERROR logs on every idle timeout.
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException ex) {
        log.debug("[SSE] Request timed out (client should auto-reconnect)");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDb(DataAccessException ex) {
        ex.getMostSpecificCause();
        String detail = ex.getMostSpecificCause().getMessage();

        ApiResponse<Void> res = ApiResponses.fail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Database error",
                List.of(ApiError.builder()
                        .code("DB_ERROR")
                        .message(detail)
                        .build())
        );

        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFoundException(NotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponses.fail(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(java.lang.IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(java.lang.IllegalStateException ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponses.fail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
        ApiResponse<Void> res = ApiResponses.fail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                List.of(ApiError.builder()
                        .code("BAD_REQUEST")
                        .message(ex.getMessage())
                        .build())
        );

        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ApiResponse<Void>> handleRestClient(RestClientResponseException ex) {

        log.error("Upstream HTTP error: status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString(), ex);

        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.BAD_GATEWAY;

        ApiResponse<Void> res = ApiResponses.fail(
                status,
                "Upstream service error",
                List.of(ApiError.builder()
                        .code("UPSTREAM_ERROR")
                        .message("HTTP " + ex.getStatusCode().value() + " " + ex.getStatusText())
                        .build())
        );

        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        ApiResponse<Void> res = ApiResponses.fail(
                HttpStatus.CONFLICT,
                ex.getMessage(),
                List.of(ApiError.builder().code("CONFLICT").message(ex.getMessage()).build())
        );
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled error", ex);

        ApiResponse<Void> res = ApiResponses.fail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error",
                List.of(ApiError.builder()
                        .code("INTERNAL_ERROR")
                        .message("An unexpected error occurred")
                        .build())
        );

        return ResponseEntity.status(res.getStatusCode()).body(res);
    }
}
