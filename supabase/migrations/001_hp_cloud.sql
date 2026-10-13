-- =====================================================================================
-- HP: Health Points — esquema en Supabase
--
-- Modelo:
--   · El TELÉFONO inicia sesión de forma anónima (auth.uid()) y es dueño de sus filas.
--   · El RELOJ no inicia sesión: se vincula con un código de 6 dígitos que muestra el
--     teléfono y recibe un "watch_token" secreto. Con él lee el estado (función
--     get_state_by_token) y escucha/envía en el canal Realtime "hp-<watch_token>".
--
-- Cómo aplicarlo: Supabase → SQL Editor → pegar todo → Run.
-- =====================================================================================

-- Estado del día (el mismo GameState que viaja al reloj, en JSON) ---------------------
create table if not exists public.players (
  user_id     uuid primary key default auth.uid() references auth.users (id) on delete cascade,
  watch_token uuid not null unique default gen_random_uuid(),
  state       jsonb not null,
  updated_at  timestamptz not null default now()
);

-- Historial: un registro por día terminado (calendario-mapa de calor) -----------------
create table if not exists public.day_history (
  user_id    uuid not null default auth.uid() references auth.users (id) on delete cascade,
  day_key    date not null,
  max_hp     int  not null check (max_hp > 0),
  hp_left    int  not null check (hp_left >= 0),
  game_over  boolean not null,
  xp_earned  int  not null default 0,
  usage      jsonb not null default '{}'::jsonb,   -- {"com.instagram.android": 31, ...}
  primary key (user_id, day_key)
);

-- Logros desbloqueados ----------------------------------------------------------------
create table if not exists public.achievements (
  user_id      uuid not null default auth.uid() references auth.users (id) on delete cascade,
  id           text not null,
  unlocked_at  timestamptz not null default now(),
  primary key (user_id, id)
);

-- Códigos de vinculación del reloj (solo se usan a través de funciones) ---------------
create table if not exists public.link_codes (
  code        text primary key check (code ~ '^[0-9]{6}$'),
  user_id     uuid not null references auth.users (id) on delete cascade,
  expires_at  timestamptz not null
);

-- Seguridad por filas: cada usuario solo ve y modifica lo suyo ------------------------
alter table public.players      enable row level security;
alter table public.day_history  enable row level security;
alter table public.achievements enable row level security;
alter table public.link_codes   enable row level security;   -- sin políticas: inaccesible directamente

drop policy if exists "players: dueño" on public.players;
create policy "players: dueño" on public.players
  for all to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

drop policy if exists "day_history: dueño" on public.day_history;
create policy "day_history: dueño" on public.day_history
  for all to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

drop policy if exists "achievements: dueño" on public.achievements;
create policy "achievements: dueño" on public.achievements
  for all to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

-- Funciones ---------------------------------------------------------------------------

-- Teléfono: genera un código de 6 dígitos válido 10 minutos.
create or replace function public.create_link_code()
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_code text;
begin
  if auth.uid() is null then
    raise exception 'Se requiere sesión';
  end if;
  delete from public.link_codes where user_id = auth.uid() or expires_at < now();
  loop
    v_code := lpad((floor(random() * 1000000))::int::text, 6, '0');
    begin
      insert into public.link_codes (code, user_id, expires_at)
      values (v_code, auth.uid(), now() + interval '10 minutes');
      exit;
    exception when unique_violation then
      -- código repetido: se intenta otro
    end;
  end loop;
  return v_code;
end;
$$;

-- Reloj: canjea el código (un solo uso) y recibe el token secreto del jugador.
create or replace function public.redeem_link_code(p_code text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_token uuid;
begin
  select p.watch_token into v_token
  from public.link_codes c
  join public.players p on p.user_id = c.user_id
  where c.code = p_code and c.expires_at > now();

  if v_token is null then
    return null;
  end if;
  delete from public.link_codes where code = p_code;
  return v_token;
end;
$$;

-- Reloj: lee el estado actual con su token.
create or replace function public.get_state_by_token(p_token uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select state from public.players where watch_token = p_token;
$$;

-- Teléfono: invalida el token actual (desvincula todos los relojes).
create or replace function public.rotate_watch_token()
returns uuid
language sql
security definer
set search_path = ''
as $$
  update public.players set watch_token = gen_random_uuid()
  where user_id = auth.uid()
  returning watch_token;
$$;

revoke all on function public.create_link_code()        from public, anon;
revoke all on function public.rotate_watch_token()      from public, anon;
revoke all on function public.redeem_link_code(text)    from public;
revoke all on function public.get_state_by_token(uuid)  from public;
grant execute on function public.create_link_code()       to authenticated;
grant execute on function public.rotate_watch_token()     to authenticated;
grant execute on function public.redeem_link_code(text)   to anon, authenticated;
grant execute on function public.get_state_by_token(uuid) to anon, authenticated;
