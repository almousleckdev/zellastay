package com.zellastay.audit.application;

import com.zellastay.audit.api.AuditEntry;
import com.zellastay.audit.api.AuditRecorder;
import com.zellastay.audit.api.AuditUnavailableException;
import com.zellastay.audit.domain.AuditEntryValidator;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
class DefaultAuditRecorder implements AuditRecorder {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultAuditRecorder.class);

  private final AuditEntryValidator validator;
  private final AuditWriter writer;
  private final MeterRegistry meters;

  DefaultAuditRecorder(AuditEntryValidator validator, AuditWriter writer, MeterRegistry meters) {
    this.validator = validator;
    this.writer = writer;
    this.meters = meters;
  }

  @Override
  public void record(AuditEntry entry) {
    validator.validate(entry);
    try {
      writer.appendInCallerTransaction(entry);
    } catch (DataAccessException e) {
      meters.counter("audit.record.failures").increment();
      throw new AuditUnavailableException(e);
    }
  }

  @Override
  public void recordBestEffort(AuditEntry entry) {
    validator.validate(entry);
    try {
      writer.appendInNewTransaction(entry);
    } catch (RuntimeException e) {
      meters.counter("audit.record.failures").increment();
      LOG.error(
          "audit entry {} could not be stored ({})",
          entry.action().code(),
          e.getClass().getSimpleName());
    }
  }
}
