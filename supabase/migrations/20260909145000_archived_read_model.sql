create or replace function public.admin_list_archived_items(p_limit integer default 100, p_offset integer default 0)
returns table(id uuid, entity_type text, name text, category text, subtitle text, can_delete boolean, blocked_reason text)
language plpgsql security invoker set search_path = '' as $$
begin
  if auth.uid() is null or coalesce(auth.jwt()->'app_metadata'->>'role','') <> 'admin' then raise exception 'ADMIN_REQUIRED'; end if;
  return query
  with entries as (
    select g.id, 'guard'::text as entity_type, g.name, 'Porteiros'::text as category, 'Porteiro arquivado'::text as subtitle,
      (exists(select 1 from public.shifts s where s.guard_id=g.id)
       or exists(select 1 from public.patrol_runs r where r.guard_id=g.id)
       or exists(select 1 from public.patrol_scans s where s.guard_id=g.id)
       or exists(select 1 from public.patrol_occurrences o where o.guard_id=g.id)
       or exists(select 1 from public.alerts a where a.guard_id=g.id)
       or exists(select 1 from public.audit_logs a where a.actor_guard_id=g.id)) as protected,
      'Este porteiro possui histórico e deve permanecer arquivado.'::text as reason
    from public.guards g where not g.active and g.e2e_run_id is null
    union all
    select f.id,'floor',f.name,'Andares',b.name,
      f.system_fixed or exists(select 1 from public.checkpoints c where c.floor_id=f.id),
      case when f.system_fixed then 'Andar fixo do condomínio.' else 'Este andar ainda possui pontos vinculados.' end
    from public.floors f join public.blocks b on b.id=f.block_id join public.buildings bd on bd.id=b.building_id
    where not f.active and bd.e2e_run_id is null
    union all
    select c.id,'checkpoint',c.name,'Pontos',b.name || ' • ' || f.name,
      c.system_fixed or exists(select 1 from public.patrol_scans s where s.checkpoint_id=c.id)
      or exists(select 1 from public.patrol_run_checkpoints r where r.checkpoint_id=c.id),
      case when c.system_fixed then 'Ponto obrigatório do condomínio.' else 'Este ponto possui histórico de ronda.' end
    from public.checkpoints c join public.floors f on f.id=c.floor_id join public.blocks b on b.id=f.block_id join public.buildings bd on bd.id=b.building_id
    where not c.active and bd.e2e_run_id is null
    union all
    select t.id,'patrol_template',t.name,'Rondas','Ronda arquivada',
      t.system_fixed or exists(select 1 from public.patrol_runs r where r.patrol_template_id=t.id),
      case when t.system_fixed then 'Ronda fixa do condomínio.' else 'Esta ronda possui histórico.' end
    from public.patrol_templates t where not t.active and t.e2e_run_id is null
  )
  select e.id,e.entity_type,e.name,e.category,e.subtitle,not e.protected,
    case when e.protected then e.reason else null::text end
  from entries e order by e.category,lower(e.name),e.id
  limit greatest(1,least(coalesce(p_limit,100),500)) offset greatest(coalesce(p_offset,0),0);
end;
$$;
revoke all on function public.admin_list_archived_items(integer,integer) from public,anon;
grant execute on function public.admin_list_archived_items(integer,integer) to authenticated;

do $patch$
declare def text;
begin
  select pg_get_functiondef('private.admin_delete_archived_entity(text,uuid)'::regprocedure) into def;
  if position('ARCHIVE_DELETE_LOCK' in def)=0 then
    def := replace(def,'  case p_entity_type',
      E'  -- ARCHIVE_DELETE_LOCK: a restore must not race a permanent deletion.\n  case p_entity_type\n    when ''guard'' then perform 1 from public.guards where id=p_id for update;\n    when ''checkpoint'' then perform 1 from public.checkpoints where id=p_id for update;\n    when ''floor'' then perform 1 from public.floors where id=p_id for update;\n    when ''patrol_template'' then perform 1 from public.patrol_templates where id=p_id for update;\n    else raise exception ''INVALID_ENTITY_TYPE'';\n  end case;\n\n  case p_entity_type');
    execute def;
  end if;
end;
$patch$;
