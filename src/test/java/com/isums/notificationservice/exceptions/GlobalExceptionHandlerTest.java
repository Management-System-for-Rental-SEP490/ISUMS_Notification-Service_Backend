package com.isums.notificationservice.exceptions;

import com.isums.notificationservice.domains.dtos.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler (notification-service)")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleNotFoundException returns 404")
    void notFound() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleNotFoundException(new NotFoundException("missing"));
        assertThat(res.getStatusCode().value()).isEqualTo(404);
        assertThat(res.getBody().getMessage()).isEqualTo("missing");
    }

    @Test
    @DisplayName("handleIllegalStateException returns 500 (fix: JDK class)")
    void illegalState() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleIllegalStateException(new IllegalStateException("boom"));
        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().getMessage()).isEqualTo("boom");
    }

    @Test
    @DisplayName("handleBadRequest returns 400")
    void badRequest() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleBadRequest(new IllegalArgumentException("bad"));
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(res.getBody().getErrors().get(0).getCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    @DisplayName("handleConflict returns 409")
    void conflict() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleConflict(new ConflictException("dup"));
        assertThat(res.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    @DisplayName("handleRestClient mirrors upstream status")
    void restClient() {
        RestClientResponseException ex = new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY, "unp");
        ResponseEntity<ApiResponse<Void>> res = handler.handleRestClient(ex);
        assertThat(res.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    @DisplayName("handleRestClient falls back to 502 for non-standard status")
    void restClientFallback() {
        RestClientResponseException ex = new RestClientResponseException(
                "weird", HttpStatusCode.valueOf(599), "server", null, null, null);
        assertThat(handler.handleRestClient(ex).getStatusCode().value()).isEqualTo(502);
    }

    @Test
    @DisplayName("handleDb returns 500 with DB_ERROR code")
    void db() {
        DataAccessException ex = new DataAccessException("outer", new RuntimeException("root")) {};
        ResponseEntity<ApiResponse<Void>> res = handler.handleDb(ex);
        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().getErrors().get(0).getMessage()).isEqualTo("root");
    }

    @Test
    @DisplayName("handleGeneric returns 500 with sanitized message")
    void generic() {
        ResponseEntity<ApiResponse<Void>> res = handler.handleGeneric(new Exception("sensitive"));
        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().getMessage()).isEqualTo("Unexpected error");
    }
}
