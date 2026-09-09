create unique index if not exists patrol_runs_one_occurrence_uq
on public.patrol_runs(schedule_window_id, scheduled_for)
where status in ('IN_PROGRESS','COMPLETED','INCOMPLETE');
create unique index if not exists patrol_runs_one_active_per_shift_uq
on public.patrol_runs(shift_id) where status='IN_PROGRESS';

create or replace function public.start_patrol_run(
 p_template_id uuid, p_schedule_window_id uuid, p_shift_id uuid, p_guard_id uuid,
 p_device_id uuid, p_scheduled_for timestamptz, p_started_at_local timestamptz,
 p_is_late boolean, p_client_event_id uuid)
returns table(run_id uuid, required_points integer, started_at_server timestamptz)
language plpgsql set search_path=public as $$
declare v_row public.patrol_runs%rowtype; v_count integer; v_run uuid; v_server timestamptz;
begin
 if p_scheduled_for is null or p_client_event_id is null then raise exception 'INVALID_PATROL_EVENT'; end if;
 perform pg_advisory_xact_lock(hashtextextended('patrol-event:'||p_client_event_id::text,0));
 select pr.* into v_row from public.patrol_runs pr where pr.client_event_id=p_client_event_id;
 if found then
   if v_row.guard_id<>p_guard_id or v_row.device_id<>p_device_id or v_row.patrol_template_id<>p_template_id
      or v_row.shift_id<>p_shift_id or v_row.schedule_window_id<>p_schedule_window_id or v_row.scheduled_for<>p_scheduled_for then
     raise exception 'INVALID_EVENT_OWNERSHIP';
   end if;
   select count(*) into v_count from public.patrol_run_checkpoints rc where rc.patrol_run_id=v_row.id and rc.required;
   return query select v_row.id,v_count,v_row.started_at_server; return;
 end if;
 if not exists(select 1 from public.guards g where g.id=p_guard_id and g.active and g.pin_state='PERSONAL') then raise exception 'GUARD_NOT_ACTIVE'; end if;
 perform 1 from public.shifts s where s.id=p_shift_id and s.guard_id=p_guard_id and s.device_id=p_device_id and s.ended_at_server is null for update;
 if not found then raise exception 'ACTIVE_SHIFT_NOT_FOUND'; end if;
 if not exists(select 1 from public.patrol_templates pt join public.devices d on d.id=p_device_id where pt.id=p_template_id and pt.active and d.status='ACTIVE' and d.building_id=pt.building_id) then raise exception 'PATROL_TEMPLATE_NOT_ACTIVE_OR_WRONG_BUILDING'; end if;
 if not exists(select 1 from public.patrol_schedule_windows w where w.id=p_schedule_window_id and w.patrol_template_id=p_template_id and w.active) then raise exception 'SCHEDULE_WINDOW_NOT_ACTIVE'; end if;
 perform pg_advisory_xact_lock(hashtextextended('patrol-occurrence:'||p_schedule_window_id::text||':'||extract(epoch from p_scheduled_for)::text,0));
 if exists(select 1 from public.patrol_runs pr where pr.schedule_window_id=p_schedule_window_id and pr.scheduled_for=p_scheduled_for and pr.status in ('IN_PROGRESS','COMPLETED','INCOMPLETE')) then raise exception 'PATROL_OCCURRENCE_ALREADY_EXECUTED'; end if;
 if exists(select 1 from public.patrol_runs pr where pr.shift_id=p_shift_id and pr.status='IN_PROGRESS') then raise exception 'PATROL_ALREADY_IN_PROGRESS'; end if;
 insert into public.patrol_runs as inserted(patrol_template_id,schedule_window_id,shift_id,guard_id,device_id,scheduled_for,started_at_local,status,is_late,captured_offline,suspicious,client_event_id)
 values(p_template_id,p_schedule_window_id,p_shift_id,p_guard_id,p_device_id,p_scheduled_for,p_started_at_local,'IN_PROGRESS',coalesce(p_is_late,false),false,false,p_client_event_id)
 returning inserted.id,inserted.started_at_server into v_run,v_server;
 insert into public.patrol_run_checkpoints(patrol_run_id,checkpoint_id,required)
 select v_run,ptc.checkpoint_id,ptc.required from public.patrol_template_checkpoints ptc join public.checkpoints c on c.id=ptc.checkpoint_id where ptc.patrol_template_id=p_template_id and ptc.active and c.active;
 select count(*) into v_count from public.patrol_run_checkpoints rc where rc.patrol_run_id=v_run and rc.required;
 if v_count=0 then raise exception 'PATROL_HAS_NO_ACTIVE_CHECKPOINTS'; end if;
 insert into public.audit_logs(actor_type,actor_guard_id,actor_device_id,action,entity_type,entity_id,metadata)
 values('GUARD',p_guard_id,p_device_id,'PATROL_STARTED','patrol_run',v_run::text,jsonb_build_object('template_id',p_template_id,'schedule_window_id',p_schedule_window_id,'is_late',p_is_late));
 return query select v_run,v_count,v_server;
end;
$$;

create or replace function public.end_guard_shift(p_shift_id uuid,p_guard_id uuid,p_device_id uuid,p_ended_at_local timestamptz)
returns void language plpgsql set search_path=public as $$
declare v_shift public.shifts%rowtype;
begin
 select s.* into v_shift from public.shifts s where s.id=p_shift_id and s.guard_id=p_guard_id and s.device_id=p_device_id for update;
 if not found then raise exception 'ACTIVE_SHIFT_NOT_FOUND'; end if;
 if v_shift.ended_at_server is not null then return; end if;
 if exists(select 1 from public.patrol_runs pr where pr.shift_id=p_shift_id and pr.status='IN_PROGRESS') then raise exception 'PATROL_IN_PROGRESS'; end if;
 update public.shifts s set ended_at_local=p_ended_at_local,ended_at_server=now() where s.id=p_shift_id;
 insert into public.audit_logs(actor_type,actor_guard_id,actor_device_id,action,entity_type,entity_id)
 values('GUARD',p_guard_id,p_device_id,'SHIFT_ENDED','shift',p_shift_id::text);
end;
$$;

create or replace function public.finish_patrol_run(p_run_id uuid,p_guard_id uuid,p_device_id uuid,p_finished_at_local timestamptz)
returns table(status text,visited_points integer,total_points integer,missing_checkpoint_ids uuid[])
language plpgsql set search_path=public as $$
declare v_row public.patrol_runs%rowtype; v_shift_id uuid; v_visited integer; v_total integer; v_missing uuid[]; v_status text; v_duration integer;
begin
 select pr.shift_id into v_shift_id from public.patrol_runs pr where pr.id=p_run_id and pr.guard_id=p_guard_id and pr.device_id=p_device_id;
 if v_shift_id is null then raise exception 'PATROL_NOT_IN_PROGRESS'; end if;
 perform 1 from public.shifts s where s.id=v_shift_id for update;
 select pr.* into v_row from public.patrol_runs pr where pr.id=p_run_id and pr.guard_id=p_guard_id and pr.device_id=p_device_id for update;
 if not found or v_row.status not in ('IN_PROGRESS','COMPLETED','INCOMPLETE') then raise exception 'PATROL_NOT_IN_PROGRESS'; end if;
 select count(*) filter(where rc.visited),count(*),coalesce(array_agg(rc.checkpoint_id) filter(where not rc.visited),'{}'::uuid[])
 into v_visited,v_total,v_missing from public.patrol_run_checkpoints rc where rc.patrol_run_id=p_run_id and rc.required;
 if v_row.status in ('COMPLETED','INCOMPLETE') then
   perform public.end_guard_shift(v_shift_id,p_guard_id,p_device_id,coalesce(v_row.finished_at_local,p_finished_at_local));
   return query select v_row.status,v_visited,v_total,v_missing; return;
 end if;
 v_status:=case when v_total>0 and v_visited=v_total then 'COMPLETED' else 'INCOMPLETE' end;
 v_duration:=greatest(0,extract(epoch from (coalesce(p_finished_at_local,now())-v_row.started_at_local))::integer);
 update public.patrol_runs pr set status=v_status,finished_at_local=p_finished_at_local,finished_at_server=now(),suspicious=pr.suspicious or (v_duration<300) where pr.id=p_run_id;
 insert into public.audit_logs(actor_type,actor_guard_id,actor_device_id,action,entity_type,entity_id,metadata)
 values('GUARD',p_guard_id,p_device_id,'PATROL_FINISHED','patrol_run',p_run_id::text,jsonb_build_object('status',v_status,'visited',v_visited,'total',v_total,'duration_seconds',v_duration,'minimum_seconds',300));
 if v_status='INCOMPLETE' then
   perform public.create_alert_once('incomplete:'||p_run_id::text,'PATROL_INCOMPLETE','CRITICAL','Ronda finalizada incompleta','A ronda foi encerrada sem visitar todos os pontos obrigatórios.',p_run_id,p_guard_id,p_device_id,jsonb_build_object('visited_points',v_visited,'total_points',v_total,'missing_checkpoint_ids',v_missing));
 end if;
 if v_duration<300 then
   perform public.create_alert_once('too-fast:'||p_run_id::text,'PATROL_TOO_FAST','WARNING','Ronda concluída rápido demais','A ronda foi concluída em menos de 5 minutos e deve ser revisada.',p_run_id,p_guard_id,p_device_id,jsonb_build_object('duration_seconds',v_duration,'minimum_seconds',300,'visited_points',v_visited,'total_points',v_total));
 end if;
 perform public.end_guard_shift(v_shift_id,p_guard_id,p_device_id,p_finished_at_local);
 return query select v_status,v_visited,v_total,v_missing;
end;
$$;

do $patch$
declare def text; start_pos integer; end_pos integer;
begin
 select pg_get_functiondef('public.register_patrol_scan(uuid,uuid,uuid,text,timestamptz,bigint,uuid)'::regprocedure) into def;
 if position('QA_RUN_SERIALIZATION' in def)=0 then
   def:=replace(def,E'begin\n',E'begin\n  -- QA_RUN_SERIALIZATION: serialize scans against completion and verify ownership before replay.\n  perform 1 from public.patrol_runs pr_lock where pr_lock.id=p_run_id and pr_lock.guard_id=p_guard_id and pr_lock.device_id=p_device_id for update;\n  if not found then raise exception ''PATROL_NOT_IN_PROGRESS''; end if;\n');
   start_pos:=position('  if v_qr.id is not null and exists(' in def);
   end_pos:=position('  if v_qr.id is null then' in def);
   if start_pos>0 and end_pos>start_pos then def:=substring(def from 1 for start_pos-1)||substring(def from end_pos); end if;
   execute def;
 end if;
end;
$patch$;
revoke all on function public.start_patrol_run(uuid,uuid,uuid,uuid,uuid,timestamptz,timestamptz,boolean,uuid) from public,anon,authenticated;
revoke all on function public.finish_patrol_run(uuid,uuid,uuid,timestamptz) from public,anon,authenticated;
revoke all on function public.end_guard_shift(uuid,uuid,uuid,timestamptz) from public,anon,authenticated;
grant execute on function public.start_patrol_run(uuid,uuid,uuid,uuid,uuid,timestamptz,timestamptz,boolean,uuid) to service_role;
grant execute on function public.finish_patrol_run(uuid,uuid,uuid,timestamptz) to service_role;
grant execute on function public.end_guard_shift(uuid,uuid,uuid,timestamptz) to service_role;
