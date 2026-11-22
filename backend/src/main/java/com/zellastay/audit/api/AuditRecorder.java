package com.zellastay.audit.api;

/** Entry point for recording audit events. Call from application services, never controllers. */
public interface AuditRecorder {

  /**
   * Records in the caller's transaction (which must exist). If recording fails the caller's
   * transaction fails too, so a privileged action never happens without its audit trail.
   */
  void record(AuditEntry entry);

  /**
   * Records in an independent transaction for security telemetry. A storage failure is logged and
   * counted but never breaks the request. An invalid entry is a programming error and still throws.
   */
  void recordBestEffort(AuditEntry entry);
}
