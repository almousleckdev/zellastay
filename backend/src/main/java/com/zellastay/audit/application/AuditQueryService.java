package com.zellastay.audit.application;

import com.zellastay.audit.domain.AuditPage;
import com.zellastay.audit.domain.AuditQuery;
import com.zellastay.audit.domain.AuditStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side for the admin audit viewer. Authorization is applied by the calling endpoint. */
@Service
public class AuditQueryService {

  private final AuditStore store;

  AuditQueryService(AuditStore store) {
    this.store = store;
  }

  @Transactional(readOnly = true)
  public AuditPage find(AuditQuery query) {
    return store.find(query);
  }
}
