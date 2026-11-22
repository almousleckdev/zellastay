package com.zellastay.identity.web;

/**
 * Response of the password step: the ticket for the second factor. Secrets never appear in
 * toString().
 */
record MfaTicketResponse(String mfaTicket, boolean mfaEnrolled) {

  @Override
  public String toString() {
    return "MfaTicketResponse[]";
  }
}
