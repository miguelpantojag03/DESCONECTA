-- =====================================================================================
-- HP: Health Points — 006 · Ajustes encontrados en la prueba de integración
--
-- 1. El upsert de app_usage (PostgREST "merge-duplicates") reescribe TODAS las columnas
--    enviadas, también las de la clave. Se permite actualizar esas columnas (el valor no
--    cambia; RLS sigue impidiendo tocar filas ajenas).
-- 2. Anti-trampas: el uso de un día ya cerrado no se puede crear ni modificar (close_day
--    lo usa para acotar la vida declarada).
-- =====================================================================================

grant update (package_name, fecha, hora) on public.app_usage to authenticated;

create or replace function public.protect_closed_usage()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if exists (select 1 from public.daily_sessions
              where user_id = new.user_id and fecha = new.fecha and cerrado) then
    raise exception 'El día % ya está cerrado', new.fecha using errcode = 'check_violation';
  end if;
  return new;
end;
$$;
revoke all on function public.protect_closed_usage() from public, anon, authenticated;

drop trigger if exists app_usage_protect on public.app_usage;
create trigger app_usage_protect
before insert or update on public.app_usage
for each row execute function public.protect_closed_usage();
