-- =====================================================================================
-- HP: Health Points — 007 · Vincular el uso con su monstruo
--
-- El teléfono sube el uso por paquete (package_name) y nunca envía monster_id. Sin este
-- vínculo, close_day veía 0 minutos por monstruo: el jefe semanal de "minutos" se daba
-- siempre por superado y el logro "sin pociones contra Scrollzilla" se desbloqueaba aunque
-- se hubiera pasado del límite. El servidor lo calcula él mismo (no se confía en el cliente).
-- =====================================================================================

create or replace function public.link_usage_monster()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.monster_id is null and not new.es_aliado then
    select m.id into new.monster_id
      from public.monsters m
     where m.user_id = new.user_id and m.package_name = new.package_name and not m.es_aliado;
  end if;
  return new;
end;
$$;
revoke all on function public.link_usage_monster() from public, anon, authenticated;

drop trigger if exists app_usage_link_monster on public.app_usage;
create trigger app_usage_link_monster
before insert or update on public.app_usage
for each row execute function public.link_usage_monster();

-- Datos ya subidos: se vinculan una vez (incluye días cerrados, por eso se suspende un
-- momento la protección de días cerrados; solo cambia monster_id, nunca los minutos).
alter table public.app_usage disable trigger app_usage_protect;
update public.app_usage u
   set monster_id = m.id
  from public.monsters m
 where u.monster_id is null and not u.es_aliado
   and m.user_id = u.user_id and m.package_name = u.package_name and not m.es_aliado;
alter table public.app_usage enable trigger app_usage_protect;
