create function audit_reject_change() returns trigger
language plpgsql as $$
begin
  raise exception 'audit_event is append-only' using errcode = '55000';
end
$$;

create table audit_event (
  id uuid not null,
  occurred_at timestamptz not null,
  actor_type text not null,
  actor_id uuid,
  actor_org_id uuid,
  action text not null,
  subject_type text not null,
  subject_id text,
  outcome text not null,
  ip text,
  user_agent text,
  correlation_id text,
  details jsonb not null default '{}'::jsonb,
  constraint audit_event_pk primary key (id, occurred_at),
  constraint audit_event_actor_type_ck
    check (actor_type in ('CUSTOMER', 'VENDOR_USER', 'ADMIN', 'SYSTEM', 'ANONYMOUS')),
  constraint audit_event_outcome_ck check (outcome in ('SUCCESS', 'DENIED', 'FAILURE'))
) partition by range (occurred_at);

create index audit_event_time_idx on audit_event (occurred_at desc, id desc);
create index audit_event_actor_idx on audit_event (actor_id, occurred_at desc);
create index audit_event_subject_idx on audit_event (subject_type, subject_id, occurred_at desc);
create index audit_event_action_idx on audit_event (action, occurred_at desc);

create trigger audit_event_no_change
  before update or delete on audit_event
  for each row execute function audit_reject_change();
create trigger audit_event_no_truncate
  before truncate on audit_event
  for each statement execute function audit_reject_change();

create table audit_event_default partition of audit_event default;
create trigger audit_event_default_no_truncate
  before truncate on audit_event_default
  for each statement execute function audit_reject_change();

create function audit_ensure_partition(month_start date) returns boolean
language plpgsql as $$
declare
  partition_name text := format('audit_event_y%sm%s', to_char(month_start, 'YYYY'), to_char(month_start, 'MM'));
  range_from timestamptz := month_start::timestamp at time zone 'UTC';
  range_to timestamptz := (month_start + interval '1 month')::timestamp at time zone 'UTC';
begin
  if to_regclass(partition_name) is not null then
    return false;
  end if;
  execute format('create table %I partition of audit_event for values from (%L) to (%L)',
                 partition_name, range_from, range_to);
  execute format('create trigger %I before truncate on %I for each statement execute function audit_reject_change()',
                 partition_name || '_no_truncate', partition_name);
  return true;
exception
  when duplicate_table then
    return false;
end
$$;

do $$
declare
  first_month date := date_trunc('month', now() at time zone 'UTC')::date;
begin
  for offset_months in 0..3 loop
    perform audit_ensure_partition((first_month + make_interval(months => offset_months))::date);
  end loop;
end
$$;
