create table ident_account (
  id uuid primary key,
  kind text not null check (kind in ('CUSTOMER', 'VENDOR_USER', 'ADMIN')),
  email text not null,
  email_verified_at timestamptz,
  phone_e164 text,
  password_hash text not null,
  status text not null
    check (status in ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED', 'ANONYMIZED')),
  locale text not null,
  country text,
  failed_login_count integer not null default 0,
  locked_until timestamptz,
  password_changed_at timestamptz not null,
  last_login_at timestamptz,
  created_at timestamptz not null,
  version bigint not null default 0,
  constraint ident_account_kind_email_uk unique (kind, email)
);

create table ident_profile (
  account_id uuid primary key references ident_account (id),
  first_name text not null,
  last_name text not null
);

create table ident_session (
  id uuid primary key,
  account_id uuid not null references ident_account (id),
  audience text not null check (audience in ('CUSTOMER', 'VENDOR', 'ADMIN')),
  mfa_verified boolean not null,
  created_at timestamptz not null,
  absolute_expires_at timestamptz not null,
  idle_timeout_seconds bigint not null,
  last_used_at timestamptz not null,
  revoked_at timestamptz,
  revoked_reason text
);
create index ident_session_account_idx on ident_session (account_id) where revoked_at is null;

create table ident_refresh_token (
  id uuid primary key,
  session_id uuid not null references ident_session (id),
  token_hash text not null unique,
  issued_at timestamptz not null,
  used_at timestamptz,
  replaced_by uuid
);
create index ident_refresh_token_session_idx on ident_refresh_token (session_id);

create table ident_challenge (
  id uuid primary key,
  account_id uuid not null references ident_account (id),
  purpose text not null check (purpose in ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
  secret_hash text not null,
  expires_at timestamptz not null,
  attempts integer not null default 0,
  consumed_at timestamptz,
  created_at timestamptz not null
);
create index ident_challenge_account_idx on ident_challenge (account_id, purpose, created_at desc);
