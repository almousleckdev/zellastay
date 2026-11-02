package com.zellastay.audit.domain;

public interface AuditStore {

  void append(AuditRecord record);

  AuditPage find(AuditQuery query);
}
