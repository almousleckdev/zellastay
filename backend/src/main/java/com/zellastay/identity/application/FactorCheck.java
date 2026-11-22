package com.zellastay.identity.application;

import java.util.OptionalLong;

/** The result of checking a second factor, with what the sign-in must do if it was accepted. */
record FactorCheck(
    boolean accepted, boolean viaRecoveryCode, OptionalLong totpStep, boolean firstEnrolment) {}
