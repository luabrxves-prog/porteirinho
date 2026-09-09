create or replace function public.rondasafe_notify_client_sync()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.client_sync_state
  set version = version + 1,
      updated_at = clock_timestamp(),
      changed_table = tg_table_name,
      changed_id = null,
      operation = tg_op
  where id = 1;

  return case when tg_op = 'DELETE' then old else new end;
end;
$$;
