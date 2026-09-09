create table if not exists public.client_sync_state (
  id smallint primary key default 1 check (id = 1),
  version bigint not null default 0,
  updated_at timestamptz not null default now(),
  changed_table text,
  changed_id text,
  operation text not null default 'BOOTSTRAP'
);

insert into public.client_sync_state (id, version, operation)
values (1, 0, 'BOOTSTRAP')
on conflict (id) do nothing;

alter table public.client_sync_state enable row level security;
revoke all on table public.client_sync_state from anon, authenticated;
grant select on table public.client_sync_state to anon, authenticated;

drop policy if exists client_sync_state_select on public.client_sync_state;
create policy client_sync_state_select
on public.client_sync_state
for select
to anon, authenticated
using (true);

alter table public.client_sync_state replica identity full;

do $$
begin
  if not exists (
    select 1
    from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'client_sync_state'
  ) then
    alter publication supabase_realtime add table public.client_sync_state;
  end if;
end
$$;

create or replace function public.rondasafe_notify_client_sync()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_row jsonb;
  v_id text;
begin
  v_row := case when tg_op = 'DELETE' then to_jsonb(old) else to_jsonb(new) end;
  v_id := coalesce(
    v_row ->> 'id',
    v_row ->> 'guard_id',
    v_row ->> 'device_id',
    v_row ->> 'qr_token_id',
    v_row ->> 'client_event_id'
  );

  update public.client_sync_state
  set version = version + 1,
      updated_at = clock_timestamp(),
      changed_table = tg_table_name,
      changed_id = v_id,
      operation = tg_op
  where id = 1;

  return case when tg_op = 'DELETE' then old else new end;
end;
$$;

alter table public.buildings add column if not exists version bigint not null default 1;
alter table public.blocks add column if not exists version bigint not null default 1;
alter table public.floors add column if not exists version bigint not null default 1;
alter table public.checkpoints add column if not exists version bigint not null default 1;
alter table public.guards add column if not exists version bigint not null default 1;
alter table public.patrol_templates add column if not exists version bigint not null default 1;
alter table public.patrol_schedule_windows add column if not exists version bigint not null default 1;
alter table public.patrol_template_checkpoints add column if not exists version bigint not null default 1;
alter table public.patrol_schedule_assignments add column if not exists updated_at timestamptz not null default now();
alter table public.patrol_schedule_assignments add column if not exists version bigint not null default 1;
alter table public.profiles add column if not exists version bigint not null default 1;
alter table public.alerts add column if not exists updated_at timestamptz not null default now();
alter table public.alerts add column if not exists version bigint not null default 1;

create or replace function public.rondasafe_touch_version()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at := clock_timestamp();
  new.version := coalesce(old.version, 0) + 1;
  return new;
end;
$$;

do $$
declare
  t text;
begin
  foreach t in array array[
    'buildings','blocks','floors','checkpoints','guards','patrol_templates',
    'patrol_schedule_windows','patrol_template_checkpoints','patrol_schedule_assignments',
    'profiles','alerts'
  ]
  loop
    execute format('drop trigger if exists trg_rondasafe_touch_version on public.%I', t);
    execute format(
      'create trigger trg_rondasafe_touch_version before update on public.%I for each row execute function public.rondasafe_touch_version()',
      t
    );
  end loop;
end
$$;

do $$
declare
  t text;
begin
  foreach t in array array[
    'buildings','blocks','floors','checkpoints','guards','guard_credentials',
    'patrol_templates','patrol_schedule_windows','patrol_template_checkpoints',
    'patrol_schedule_assignments','qr_tokens','qr_token_secrets','alerts',
    'patrol_runs','patrol_scans','patrol_occurrences','shifts','profiles'
  ]
  loop
    execute format('drop trigger if exists trg_rondasafe_client_sync on public.%I', t);
    execute format(
      'create trigger trg_rondasafe_client_sync after insert or update or delete on public.%I for each row execute function public.rondasafe_notify_client_sync()',
      t
    );
  end loop;
end
$$;

create or replace function public.admin_update_fixed_patrol_times(
  p_template_id uuid,
  p_start_time time,
  p_end_time time,
  p_expected_versions jsonb
)
returns void
language plpgsql
security invoker
set search_path = public
as $$
begin
  if coalesce(auth.jwt() -> 'app_metadata' ->> 'role', '') <> 'admin' then
    raise exception 'ADMIN_REQUIRED';
  end if;

  if not exists (
    select 1 from public.patrol_templates
    where id = p_template_id and active and system_fixed
  ) then
    raise exception 'FIXED_PATROL_NOT_FOUND';
  end if;

  if exists (
    select 1
    from public.patrol_schedule_windows w
    where w.patrol_template_id = p_template_id
      and w.active
      and coalesce((p_expected_versions ->> w.id::text)::bigint, -1) <> w.version
  ) then
    raise exception 'CONFLICT_VERSION_MISMATCH';
  end if;

  update public.patrol_schedule_windows
  set start_time = p_start_time,
      end_time = p_end_time
  where patrol_template_id = p_template_id
    and active;
end;
$$;

grant execute on function public.admin_update_fixed_patrol_times(uuid,time,time,jsonb) to authenticated;
