package com.zellastay.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.zellastay.audit.api.AuditActor;
import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditOutcome;
import com.zellastay.audit.api.AuditRecorder;
import com.zellastay.audit.api.AuditSubject;
import com.zellastay.audit.api.AuditUnavailableException;
import com.zellastay.audit.domain.AuditStore;
import com.zellastay.support.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class AuditStoreFailureIT {

  @Autowired AuditRecorder recorder;
  @Autowired PlatformTransactionManager transactions;
  @Autowired MeterRegistry meters;
  @MockitoSpyBean AuditStore store;

  private static AuditEntry entry() {
    return new AuditEntry(
        AuditActor.system(),
        TestAuditAction.LOGIN_SUCCESS,
        new AuditSubject("account", UUID.randomUUID().toString()),
        AuditOutcome.FAILURE,
        Map.of());
  }

  private void storeFails() {
    doThrow(new DataAccessResourceFailureException("db down")).when(store).append(any());
  }

  @Test
  void aStorageFailureOfAPrivilegedEntryFailsTheCaller() {
    storeFails();

    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactions)
                    .executeWithoutResult(status -> recorder.record(entry())))
        .isInstanceOf(AuditUnavailableException.class);
  }

  @Test
  void aStorageFailureOfABestEffortEntryIsSwallowedAndCounted() {
    storeFails();
    double before = meters.counter("audit.record.failures").count();

    recorder.recordBestEffort(entry());

    assertThat(meters.counter("audit.record.failures").count()).isEqualTo(before + 1);
  }
}
