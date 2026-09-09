lock table public.alerts in share row exclusive mode;
with ranked as (
 select id,first_value(id) over(partition by device_id,metadata->>'shift_id' order by created_at,id) as kept_id,
 row_number() over(partition by device_id,metadata->>'shift_id' order by created_at,id) as rn
 from public.alerts where alert_type='DEVICE_SYNC_STALE' and resolved_at is null and metadata->>'shift_id' is not null
)
update public.alerts a set resolved_at=now(),metadata=a.metadata||jsonb_build_object('resolution_reason','DUPLICATE_CONSOLIDATED','consolidated_into',r.kept_id)
from ranked r where r.id=a.id and r.rn>1;
update public.alerts a set source_key='device-stale:'||a.device_id::text||':shift:'||(a.metadata->>'shift_id')
where a.alert_type='DEVICE_SYNC_STALE' and a.resolved_at is null and a.metadata->>'shift_id' is not null
and not exists(select 1 from public.alerts b where b.source_key='device-stale:'||a.device_id::text||':shift:'||(a.metadata->>'shift_id') and b.id<>a.id);
create unique index if not exists alerts_one_open_stale_shift_uq on public.alerts(device_id,(metadata->>'shift_id'))
where alert_type='DEVICE_SYNC_STALE' and resolved_at is null and metadata->>'shift_id' is not null;

create or replace function public.rondasafe_reconcile_sync_alerts()
returns void language sql security definer set search_path='' as $$
 update public.alerts a set resolved_at=now(),metadata=a.metadata||jsonb_build_object('resolution_reason',case when d.e2e_run_id is not null then 'TEST_FIXTURE' when s.ended_at_server is not null then 'SHIFT_ENDED' when d.status<>'ACTIVE' then 'DEVICE_DISABLED' else 'DEVICE_CONTACT_RESTORED' end)
 from public.devices d, public.shifts s
 where a.alert_type='DEVICE_SYNC_STALE' and a.resolved_at is null and a.device_id=d.id and a.metadata->>'shift_id'=s.id::text
 and (s.ended_at_server is not null or d.status<>'ACTIVE' or d.e2e_run_id is not null or coalesce(d.last_sync_at,d.first_seen_at)>=now()-interval '30 minutes');
$$;
revoke all on function public.rondasafe_reconcile_sync_alerts() from public,anon,authenticated;
grant execute on function public.rondasafe_reconcile_sync_alerts() to service_role;
create or replace function public.rondasafe_resolve_contact_alerts()
returns trigger language plpgsql security definer set search_path='' as $$
begin
 if tg_table_name='devices' then
  if new.last_sync_at is distinct from old.last_sync_at or new.status is distinct from old.status then perform public.rondasafe_reconcile_sync_alerts(); end if;
 elsif new.ended_at_server is distinct from old.ended_at_server then perform public.rondasafe_reconcile_sync_alerts();
 end if;
 return new;
end;
$$;
revoke all on function public.rondasafe_resolve_contact_alerts() from public,anon,authenticated;
drop trigger if exists devices_resolve_sync_alerts on public.devices;
create trigger devices_resolve_sync_alerts after update of last_sync_at,status on public.devices for each row execute function public.rondasafe_resolve_contact_alerts();
drop trigger if exists shifts_resolve_sync_alerts on public.shifts;
create trigger shifts_resolve_sync_alerts after update of ended_at_server on public.shifts for each row execute function public.rondasafe_resolve_contact_alerts();

do $patch$
declare def text;
begin
 select pg_get_functiondef('public.scan_operational_alerts()'::regprocedure) into def;
 def:=replace(def,$old$'device-stale:' || r.device_id::text || ':' || date_trunc('hour', now())::text$old$,$new$'device-stale:' || r.device_id::text || ':shift:' || r.shift_id::text$new$);
 def:=replace(def,'where pt.active','where pt.active and pt.e2e_run_id is null and b.e2e_run_id is null');
 def:=replace(def,$old$where d.status = 'ACTIVE'$old$,$new$where d.status = 'ACTIVE' and d.e2e_run_id is null$new$);
 if position('perform public.rondasafe_reconcile_sync_alerts()' in def)=0 then def:=replace(def,E'begin\n',E'begin\n  perform public.rondasafe_reconcile_sync_alerts();\n'); end if;
 def:=replace(def,$old$    perform public.create_alert_once(
      'device-stale:'$old$,$new$    update public.alerts a set resolved_at=null,read_at=null,metadata=a.metadata||jsonb_build_object('last_sync_at',r.last_sync_at,'resolution_reason',null)
    where a.source_key='device-stale:'||r.device_id::text||':shift:'||r.shift_id::text
      and a.resolved_at is not null
      and a.metadata->>'resolution_reason'='DEVICE_CONTACT_RESTORED'
      and a.metadata->>'last_sync_at' is distinct from to_jsonb(r.last_sync_at)#>>'{}';
    perform public.create_alert_once(
      'device-stale:'$new$);
 execute def;
 select pg_get_functiondef('private.admin_delete_archived_entity(text,uuid)'::regprocedure) into def;
 if position('FIXED_ENTITY_CANNOT_BE_DELETED' in def)=0 then
  def:=replace(def,'  -- ARCHIVE_DELETE_LOCK',E'  if (p_entity_type=''floor'' and exists(select 1 from public.floors where id=p_id and system_fixed))\n    or (p_entity_type=''checkpoint'' and exists(select 1 from public.checkpoints where id=p_id and system_fixed))\n    or (p_entity_type=''patrol_template'' and exists(select 1 from public.patrol_templates where id=p_id and system_fixed)) then\n    raise exception ''FIXED_ENTITY_CANNOT_BE_DELETED'';\n  end if;\n  -- ARCHIVE_DELETE_LOCK');
  execute def;
 end if;
end;
$patch$;
update public.alerts a set resolved_at=now(),metadata=a.metadata||jsonb_build_object('resolution_reason','TEST_FIXTURE')
where a.resolved_at is null and exists(select 1 from public.devices d where d.id=a.device_id and d.e2e_run_id is not null);
select public.rondasafe_reconcile_sync_alerts();
