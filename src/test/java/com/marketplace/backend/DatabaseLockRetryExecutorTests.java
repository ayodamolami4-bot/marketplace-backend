package com.marketplace.backend;

import com.marketplace.backend.common.DatabaseLockRetryExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DatabaseLockRetryExecutorTests {

    @Test
    void temporaryLockFailureIsRetriedAndEventuallySucceeds() {

        PlatformTransactionManager transactionManager =
                createTransactionManager();

        DatabaseLockRetryExecutor executor =
                new DatabaseLockRetryExecutor(
                        transactionManager,
                        3,
                        0,
                        0
                );

        AtomicInteger calls =
                new AtomicInteger();

        String result =
                executor.execute(
                        "test-lock-operation",
                        () -> {

                            int attempt =
                                    calls.incrementAndGet();

                            if (attempt == 1) {
                                throw new CannotAcquireLockException(
                                        "Temporary lock failure"
                                );
                            }

                            return "success";
                        }
                );

        assertEquals(
                "success",
                result
        );

        assertEquals(
                2,
                calls.get()
        );

        verify(
                transactionManager,
                times(2)
        ).getTransaction(
                any(TransactionDefinition.class)
        );

        verify(
                transactionManager,
                times(1)
        ).rollback(
                any()
        );

        verify(
                transactionManager,
                times(1)
        ).commit(
                any()
        );
    }

    @Test
    void postgresDeadlockSqlStateIsRetried() {

        PlatformTransactionManager transactionManager =
                createTransactionManager();

        DatabaseLockRetryExecutor executor =
                new DatabaseLockRetryExecutor(
                        transactionManager,
                        3,
                        0,
                        0
                );

        AtomicInteger calls =
                new AtomicInteger();

        String result =
                executor.execute(
                        "deadlock-test",
                        () -> {

                            int attempt =
                                    calls.incrementAndGet();

                            if (attempt == 1) {

                                SQLException deadlock =
                                        new SQLException(
                                                "deadlock detected",
                                                "40P01"
                                        );

                                throw new RuntimeException(
                                        deadlock
                                );
                            }

                            return "recovered";
                        }
                );

        assertEquals(
                "recovered",
                result
        );

        assertEquals(
                2,
                calls.get()
        );

        verify(
                transactionManager,
                times(2)
        ).getTransaction(
                any(TransactionDefinition.class)
        );

        verify(
                transactionManager,
                times(1)
        ).rollback(
                any()
        );

        verify(
                transactionManager,
                times(1)
        ).commit(
                any()
        );
    }

    @Test
    void normalBusinessFailureIsNotRetried() {

        PlatformTransactionManager transactionManager =
                createTransactionManager();

        DatabaseLockRetryExecutor executor =
                new DatabaseLockRetryExecutor(
                        transactionManager,
                        3,
                        0,
                        0
                );

        AtomicInteger calls =
                new AtomicInteger();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                executor.execute(
                                        "business-error",
                                        () -> {

                                            calls.incrementAndGet();

                                            throw new IllegalArgumentException(
                                                    "Insufficient stock"
                                            );
                                        }
                                )
                );

        assertEquals(
                "Insufficient stock",
                exception.getMessage()
        );

        assertEquals(
                1,
                calls.get()
        );

        verify(
                transactionManager,
                times(1)
        ).getTransaction(
                any(TransactionDefinition.class)
        );

        verify(
                transactionManager,
                times(1)
        ).rollback(
                any()
        );

        verify(
                transactionManager,
                never()
        ).commit(
                any()
        );
    }

    @Test
    void lockFailureStopsAfterMaximumAttempts() {

        PlatformTransactionManager transactionManager =
                createTransactionManager();

        DatabaseLockRetryExecutor executor =
                new DatabaseLockRetryExecutor(
                        transactionManager,
                        3,
                        0,
                        0
                );

        AtomicInteger calls =
                new AtomicInteger();

        CannotAcquireLockException exception =
                assertThrows(
                        CannotAcquireLockException.class,
                        () ->
                                executor.execute(
                                        "persistent-lock-failure",
                                        () -> {

                                            calls.incrementAndGet();

                                            throw new CannotAcquireLockException(
                                                    "Still locked"
                                            );
                                        }
                                )
                );

        assertEquals(
                "Still locked",
                exception.getMessage()
        );

        assertEquals(
                3,
                calls.get()
        );

        verify(
                transactionManager,
                times(3)
        ).getTransaction(
                any(TransactionDefinition.class)
        );

        verify(
                transactionManager,
                times(3)
        ).rollback(
                any()
        );

        verify(
                transactionManager,
                never()
        ).commit(
                any()
        );
    }

    private static PlatformTransactionManager
    createTransactionManager() {

        PlatformTransactionManager transactionManager =
                mock(
                        PlatformTransactionManager.class
                );

        when(
                transactionManager.getTransaction(
                        any(TransactionDefinition.class)
                )
        ).thenAnswer(
                invocation ->
                        new SimpleTransactionStatus()
        );

        return transactionManager;
    }
}