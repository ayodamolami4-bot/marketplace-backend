package com.marketplace.backend.common;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.Set;
import java.util.function.Supplier;

@Component
public class DatabaseLockRetryExecutor {

    private static final Logger log =
            LoggerFactory.getLogger(
                    DatabaseLockRetryExecutor.class
            );

    private static final Set<String>
            RETRYABLE_SQL_STATES =
            Set.of(
                    "40P01", // PostgreSQL deadlock detected
                    "55P03", // lock not available / lock timeout
                    "40001"  // serialization failure
            );

    private final PlatformTransactionManager
            transactionManager;

    private final int maxAttempts;
    private final long initialBackoffMs;
    private final long maxBackoffMs;

    public DatabaseLockRetryExecutor(
            PlatformTransactionManager transactionManager,
            @Value(
                    "${database.lock-retry.max-attempts:3}"
            )
            int maxAttempts,
            @Value(
                    "${database.lock-retry.initial-backoff-ms:50}"
            )
            long initialBackoffMs,
            @Value(
                    "${database.lock-retry.max-backoff-ms:250}"
            )
            long maxBackoffMs
    ) {

        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "maxAttempts must be at least 1"
            );
        }

        if (initialBackoffMs < 0 ||
                maxBackoffMs < 0) {

            throw new IllegalArgumentException(
                    "Database retry backoff cannot be negative"
            );
        }

        this.transactionManager =
                transactionManager;

        this.maxAttempts =
                maxAttempts;

        this.initialBackoffMs =
                initialBackoffMs;

        this.maxBackoffMs =
                maxBackoffMs;
    }

    public <T> T execute(
            String operationName,
            Supplier<T> operation
    ) {

        if (operation == null) {
            throw new IllegalArgumentException(
                    "Database operation is required"
            );
        }

        String safeOperationName =
                operationName == null ||
                        operationName.isBlank()
                        ? "database-operation"
                        : operationName;

        RuntimeException lastFailure =
                null;

        for (int attemptNumber = 1;
             attemptNumber <= maxAttempts;
             attemptNumber++) {

            try {

                /*
                 * Every retry gets a completely new
                 * transaction.
                 *
                 * A transaction that encountered a
                 * deadlock/lock timeout cannot safely
                 * be reused.
                 */
                TransactionTemplate template =
                        new TransactionTemplate(
                                transactionManager
                        );

                template.setPropagationBehavior(
                        TransactionDefinition
                                .PROPAGATION_REQUIRES_NEW
                );

                return template.execute(
                        status ->
                                operation.get()
                );

            } catch (RuntimeException exception) {

                if (!isRetryableLockFailure(
                        exception
                )) {
                    throw exception;
                }

                lastFailure =
                        exception;

                if (attemptNumber >=
                        maxAttempts) {

                    log.error(
                            "Database lock operation '{}' failed after {} attempt(s)",
                            safeOperationName,
                            attemptNumber,
                            exception
                    );

                    throw exception;
                }

                long backoff =
                        calculateBackoff(
                                attemptNumber
                        );

                log.warn(
                        "Temporary database lock failure during '{}'. Retry {}/{} after {} ms",
                        safeOperationName,
                        attemptNumber + 1,
                        maxAttempts,
                        backoff
                );

                sleep(
                        backoff,
                        safeOperationName
                );
            }
        }

        throw lastFailure == null
                ? new IllegalStateException(
                "Database retry operation ended unexpectedly"
        )
                : lastFailure;
    }

    public void executeWithoutResult(
            String operationName,
            Runnable operation
    ) {

        execute(
                operationName,
                () -> {

                    operation.run();

                    return null;
                }
        );
    }

    private boolean isRetryableLockFailure(
            Throwable throwable
    ) {

        Throwable current =
                throwable;

        while (current != null) {

            if (current instanceof
                    PessimisticLockingFailureException) {

                return true;
            }

            if (current instanceof
                    LockTimeoutException) {

                return true;
            }

            if (current instanceof
                    PessimisticLockException) {

                return true;
            }

            if (current instanceof
                    SQLException sqlException) {

                String sqlState =
                        sqlException.getSQLState();

                if (sqlState != null &&
                        RETRYABLE_SQL_STATES
                                .contains(
                                        sqlState
                                )) {

                    return true;
                }
            }

            current =
                    current.getCause();
        }

        return false;
    }

    private long calculateBackoff(
            int completedAttempt
    ) {

        if (initialBackoffMs == 0) {
            return 0;
        }

        long multiplier =
                1L <<
                        Math.min(
                                completedAttempt - 1,
                                20
                        );

        long calculated;

        try {

            calculated =
                    Math.multiplyExact(
                            initialBackoffMs,
                            multiplier
                    );

        } catch (ArithmeticException exception) {

            calculated =
                    Long.MAX_VALUE;
        }

        return Math.min(
                calculated,
                maxBackoffMs
        );
    }

    private void sleep(
            long backoffMs,
            String operationName
    ) {

        if (backoffMs <= 0) {
            return;
        }

        try {

            Thread.sleep(
                    backoffMs
            );

        } catch (InterruptedException exception) {

            Thread.currentThread()
                    .interrupt();

            throw new IllegalStateException(
                    "Interrupted while retrying "
                            + operationName,
                    exception
            );
        }
    }
}