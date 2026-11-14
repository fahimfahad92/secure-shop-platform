package com.fahim.productservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Finding #31: the client gets the same generic answer whether the pool is busy or the database is
 * unreachable: no database detail, no exception name, no stack trace. The log level tells
 * developers which one it was. The exceptions are built the way HikariCP 6.3.2 builds its timeout
 * ({@code HikariPool.createTimeoutException}): the last connection failure, if any, is the cause.
 */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private static final String GENERIC_503 = "Service temporarily unavailable, try again later";

    private static final String TIMEOUT =
            "product-service-pool - Connection is not available, request timed out after 3000ms"
                    + " (total=5, active=5, idle=0, waiting=1)";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void poolBusy_answers503_logsWarn(CapturedOutput output) {
        ResponseEntity<Map<String, Object>> response =
                handler.handleNoTransaction(
                        noTransaction(new SQLTransientConnectionException(TIMEOUT)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        assertThat(response.getBody()).containsEntry("message", GENERIC_503);
        assertGenericBody(response);
        assertThat(output.getOut())
                .contains("WARN")
                .contains("DB pool exhausted")
                .doesNotContain("ERROR");
    }

    @Test
    void dbUnreachable_answers503_logsError(CapturedOutput output) {
        SQLException refused = new SQLException("Connection to localhost:5432 refused", "08001");

        ResponseEntity<Map<String, Object>> response =
                handler.handleNoTransaction(
                        noTransaction(
                                new SQLTransientConnectionException(TIMEOUT, "08001", 0, refused)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        assertThat(response.getBody()).containsEntry("message", GENERIC_503);
        assertGenericBody(response);
        assertThat(output.getOut())
                .contains("ERROR")
                .contains("DB unreachable")
                .contains("Connection to localhost:5432 refused");
    }

    @Test
    void otherFailure_answers500_logsError(CapturedOutput output) {
        ResponseEntity<Map<String, Object>> response =
                handler.handleNoTransaction(
                        new CannotCreateTransactionException(
                                "Could not open JPA EntityManager for transaction",
                                new IllegalStateException("boom")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("message", "Internal error");
        assertGenericBody(response);
        assertThat(output.getOut()).contains("ERROR");
    }

    private static CannotCreateTransactionException noTransaction(SQLException poolTimeout) {
        return new CannotCreateTransactionException(
                "Could not open JPA EntityManager for transaction", poolTimeout);
    }

    /**
     * The client sees only the four generic fields: no stack trace, no exception name, no database
     * or pool detail. Stack traces go to the server log only.
     */
    private static void assertGenericBody(ResponseEntity<Map<String, Object>> response) {
        assertThat(response.getBody()).containsOnlyKeys("timestamp", "status", "error", "message");
        assertThat(String.valueOf(response.getBody()))
                .doesNotContainIgnoringCase("database")
                .doesNotContain("SQL")
                .doesNotContain("Exception")
                .doesNotContain("pool")
                .doesNotContain("\tat ")
                .doesNotContain("trace");
    }
}
