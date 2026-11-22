package com.zellastay.identity.api;

/** Which message the notification module should render and send. */
public enum DeliveryTemplate {
  EMAIL_VERIFICATION,
  PASSWORD_RESET,
  ACCOUNT_ALREADY_EXISTS,
  PASSWORD_CHANGED,
  ADMIN_INVITATION
}
