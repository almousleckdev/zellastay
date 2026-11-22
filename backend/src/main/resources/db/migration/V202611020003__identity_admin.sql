alter table ident_challenge drop constraint ident_challenge_purpose_check;
alter table ident_challenge
  add constraint ident_challenge_purpose_check
  check (purpose in ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'ADMIN_MFA_LOGIN'));

create table ident_admin_role (
  account_id uuid not null references ident_account (id),
  role text not null
    check (role in ('SUPER_ADMIN', 'VERIFICATION_REVIEWER', 'SUPPORT_AGENT', 'FINANCE_ADMIN', 'CONTENT_MANAGER')),
  granted_at timestamptz not null,
  primary key (account_id, role)
);

-- Reference data: which permissions each admin role carries. Later slices add their own rows.
create table ident_admin_role_permission (
  role text not null,
  permission text not null,
  primary key (role, permission)
);
insert into ident_admin_role_permission (role, permission) values
  ('SUPER_ADMIN', 'account.suspend'),
  ('SUPER_ADMIN', 'account.reinstate'),
  ('SUPER_ADMIN', 'session.revoke'),
  ('SUPER_ADMIN', 'audit.read'),
  ('SUPER_ADMIN', 'admin.manage');

create table ident_admin_mfa (
  account_id uuid primary key references ident_account (id),
  secret_ciphertext text not null,
  enrolled_at timestamptz,
  last_used_step bigint not null default 0
);

create table ident_recovery_code (
  account_id uuid not null references ident_account (id),
  code_hash text not null,
  used_at timestamptz,
  primary key (account_id, code_hash)
);
