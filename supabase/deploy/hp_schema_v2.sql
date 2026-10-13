-- =====================================================================================
-- HP: Health Points · ESQUEMA COMPLETO v2 (migraciones 002 + 003 + 004 + 005 juntas)
-- Pegar entero en Supabase → SQL Editor → Run. Reemplaza el esquema de prueba 001.
-- Generado a partir de supabase/migrations/. No editar aquí: editar las migraciones.
-- =====================================================================================

-- =====================================================================================
-- HP: Health Points — 002 · Esquema principal (juego + IA)
--
-- Reemplaza el esquema de prueba 001 (players, link_codes…): el reloj ya no se conecta
-- a Supabase; todo pasa por el teléfono.
--
-- Principios:
--   · RLS en TODAS las tablas: cada usuario solo ve y modifica lo suyo.
--   · Catálogos de solo lectura.
--   · Anti-trampas: XP, nivel, racha, inventario, logros y retos NO se pueden escribir
--     desde la app (permisos por columna); solo cambian mediante funciones (003).
--   · CHECK en todos los valores con reglas: HP y cantidades ≥ 0, máx. 3 pociones,
--     límites entre 5 y 600 minutos, enums de texto.
-- =====================================================================================

-- ---------------------------------------------------------------------------------
-- 0. Limpieza del esquema de prueba (001)
-- ---------------------------------------------------------------------------------
drop function if exists public.create_link_code();
drop function if exists public.redeem_link_code(text);
drop function if exists public.get_state_by_token(uuid);
drop function if exists public.rotate_watch_token();
drop table if exists public.link_codes;
drop table if exists public.players;
drop table if exists public.day_history;
drop table if exists public.achievements cascade;   -- la versión 001 era por usuario; ahora es catálogo

create extension if not exists pgcrypto with schema extensions;

-- ---------------------------------------------------------------------------------
-- 1. Utilidades
-- ---------------------------------------------------------------------------------
create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

-- ---------------------------------------------------------------------------------
-- 2. Catálogos (solo lectura)
-- ---------------------------------------------------------------------------------
create table public.monster_catalog (
  id                  text primary key,                       -- 'scrollzilla'
  nombre              text not null,
  descripcion         text not null,
  ilustracion_path    text not null,                          -- ruta en el bucket game-assets
  categoria           text not null check (categoria in ('social', 'video', 'juegos', 'chat', 'otra')),
  feroz               boolean not null default false,
  limite_sugerido     int  not null check (limite_sugerido between 5 and 600),
  paquetes_sugeridos  text[] not null default '{}'
);

create table public.achievements (
  id           text primary key,                              -- 'streak_7'
  titulo       text not null,
  descripcion  text not null,
  icono_path   text not null,
  orden        int  not null default 0
);

create table public.avatar_items (
  id               text primary key,                          -- 'capa'
  nombre           text not null,
  tipo             text not null check (tipo in ('estilo', 'accesorio')),
  nivel_requerido  int  not null default 1 check (nivel_requerido between 1 and 50),
  imagen_path      text not null
);

create table public.boss_catalog (
  id             text primary key,
  nombre         text not null,
  descripcion    text not null,
  meta           text not null,                               -- clave de la métrica (ver close_day)
  objetivo       int  not null check (objetivo >= 0),
  dificultad     smallint not null check (dificultad between 1 and 5),
  recompensa_id  text references public.avatar_items (id),
  categoria      text check (categoria in ('social', 'video', 'juegos', 'chat', 'noche', 'general'))
);

-- ---------------------------------------------------------------------------------
-- 3. Datos del jugador
-- ---------------------------------------------------------------------------------
create table public.profiles (
  id                      uuid primary key references auth.users (id) on delete cascade,
  nombre_jugador          text not null default 'Jugador' check (char_length(nombre_jugador) between 1 and 16),
  avatar_id               text not null default 'indigo' references public.avatar_items (id),
  nivel                   int  not null default 1 check (nivel between 1 and 50),
  xp                      int  not null default 0 check (xp >= 0),
  titulo                  text not null default 'Novato del Scroll',
  vida_maxima             int  not null default 120 check (vida_maxima between 30 and 600),
  bonus_fin_semana        int  not null default 0 check (bonus_fin_semana between 0 and 120),
  racha_actual            int  not null default 0 check (racha_actual >= 0),
  racha_maxima            int  not null default 0 check (racha_maxima >= 0),
  zona_horaria            text not null default 'America/Mexico_City',
  tono_coach              text not null default 'motivador' check (tono_coach in ('motivador', 'divertido', 'sabio', 'directo')),
  modo_familiar           boolean not null default false,
  pin_hash                text,                                -- bcrypt (set_pin); nunca el PIN
  revivir_disponible_en   timestamptz not null default now(),
  consentimiento_nube_en  timestamptz,                         -- consentimiento de sincronización
  created_at              timestamptz not null default now(),
  updated_at              timestamptz not null default now(),
  check (racha_maxima >= racha_actual)
);

create table public.monsters (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null default auth.uid() references auth.users (id) on delete cascade,
  package_name     text not null check (char_length(package_name) between 3 and 255),
  nombre_app       text not null check (char_length(nombre_app) between 1 and 60),
  nombre_monstruo  text not null check (char_length(nombre_monstruo) between 1 and 28),
  ilustracion_id   text not null default 'fantasma' references public.monster_catalog (id),
  limite_minutos   int  not null default 30 check (limite_minutos between 5 and 600 and limite_minutos % 5 = 0),
  es_aliado        boolean not null default false,
  activo           boolean not null default true,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  unique (user_id, package_name)
);

create table public.daily_sessions (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null default auth.uid() references auth.users (id) on delete cascade,
  fecha           date not null,
  hp_inicial      int  not null check (hp_inicial between 1 and 720),
  hp_final        int  not null check (hp_final >= 0),
  hp_regenerado   int  not null default 0 check (hp_regenerado between 0 and 20),
  pociones_usadas int  not null default 0 check (pociones_usadas between 0 and 10),
  game_over       boolean not null default false,
  hora_game_over  timestamptz,
  xp_ganada       int  not null default 0 check (xp_ganada >= 0),   -- solo la escribe close_day
  cerrado         boolean not null default false,                   -- solo lo escribe close_day
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  unique (user_id, fecha),
  check (hp_final <= hp_inicial),
  check (hora_game_over is null or game_over)
);

-- Uso agregado por HORA (nunca eventos detallados ni contenido).
create table public.app_usage (
  id            bigint generated always as identity primary key,
  user_id       uuid not null default auth.uid() references auth.users (id) on delete cascade,
  monster_id    uuid references public.monsters (id) on delete set null,
  package_name  text not null,
  fecha         date not null,
  hora          smallint not null check (hora between 0 and 23),
  minutos       smallint not null check (minutos between 0 and 60),
  es_aliado     boolean not null default false,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  unique (user_id, fecha, hora, package_name)
);

-- Inventario = resultado de item_events (lo recalcula un trigger). Solo lectura para la app.
create table public.inventory (
  user_id      uuid not null references auth.users (id) on delete cascade,
  tipo_objeto  text not null check (tipo_objeto in ('pocion', 'escudo', 'revivir')),
  cantidad     int  not null default 0 check (cantidad >= 0),
  updated_at   timestamptz not null default now(),
  primary key (user_id, tipo_objeto),
  check (tipo_objeto <> 'pocion'  or cantidad <= 3),
  check (tipo_objeto <> 'escudo'  or cantidad <= 3),
  check (tipo_objeto <> 'revivir' or cantidad <= 1)
);

create table public.item_events (
  id           bigint generated always as identity primary key,
  user_id      uuid not null references auth.users (id) on delete cascade,
  tipo_objeto  text not null check (tipo_objeto in ('pocion', 'escudo', 'revivir')),
  accion       text not null check (accion in ('ganado', 'usado')),
  origen       text not null check (origen in ('reloj', 'telefono', 'voz', 'sistema')),
  motivo       text,                                      -- 'racha_3', 'pantalla_bloqueo'…
  monster_id   uuid references public.monsters (id) on delete set null,
  fecha        date,                                      -- día de juego al que aplica (usados)
  client_id    uuid unique,                               -- idempotencia de la sincronización offline
  created_at   timestamptz not null default now()
);

create table public.user_achievements (
  user_id          uuid not null references auth.users (id) on delete cascade,
  achievement_id   text not null references public.achievements (id),
  desbloqueado_en  timestamptz not null default now(),
  primary key (user_id, achievement_id)
);

create table public.user_avatar_items (
  user_id      uuid not null references auth.users (id) on delete cascade,
  item_id      text not null references public.avatar_items (id),
  equipado     boolean not null default false,
  obtenido_en  timestamptz not null default now(),
  primary key (user_id, item_id)
);

create table public.weekly_challenges (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null references auth.users (id) on delete cascade,
  semana           date not null check (extract(isodow from semana) = 1),   -- siempre lunes
  nombre_jefe      text not null,
  descripcion      text not null,
  meta             text not null,
  objetivo         int  not null check (objetivo >= 0),
  progreso         int  not null default 0 check (progreso >= 0),
  estado           text not null default 'activo' check (estado in ('activo', 'superado', 'fallido')),
  dificultad       smallint not null default 3 check (dificultad between 1 and 5),
  recompensa_id    text references public.avatar_items (id),
  generado_por_ia  boolean not null default false,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  unique (user_id, semana)
);

create table public.devices (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null default auth.uid() references auth.users (id) on delete cascade,
  tipo             text not null check (tipo in ('telefono', 'reloj')),
  modelo           text not null check (char_length(modelo) between 1 and 80),
  ultima_conexion  timestamptz not null default now(),
  unique (user_id, tipo, modelo)
);

-- ---------------------------------------------------------------------------------
-- 4. IA
-- ---------------------------------------------------------------------------------
create table public.ai_settings (
  user_id                      uuid primary key references auth.users (id) on delete cascade,
  oraculo_activo               boolean not null default true,
  deteccion_compulsiva_activa  boolean not null default true,
  coach_activo                 boolean not null default true,
  voz_activa                   boolean not null default true,
  resumenes_activos            boolean not null default true,
  jefes_ia_activos             boolean not null default true,
  limites_activos              boolean not null default true,
  contexto_activo              boolean not null default false,
  tono_coach                   text not null default 'motivador' check (tono_coach in ('motivador', 'divertido', 'sabio', 'directo')),
  frecuencia_coach_minutos     int  not null default 45 check (frecuencia_coach_minutos >= 45),
  nube_permitida               boolean not null default false,
  nube_consentida_en           timestamptz,
  aprendizaje_iniciado_en      timestamptz not null default now(),
  updated_at                   timestamptz not null default now(),
  check (not nube_permitida or nube_consentida_en is not null)
);

create table public.ai_insights (
  id                 uuid primary key default gen_random_uuid(),
  user_id            uuid not null default auth.uid() references auth.users (id) on delete cascade,
  tipo               text not null check (tipo in ('resumen_diario', 'resumen_semanal', 'sugerencia_limite',
                                                   'prediccion', 'alerta_compulsiva', 'mensaje_coach')),
  contenido          text not null check (char_length(contenido) between 1 and 1000),
  motivo             text not null default '' check (char_length(motivo) <= 300),
  confianza          text not null check (confianza in ('baja', 'media', 'alta')),
  origen             text not null check (origen in ('reglas', 'dispositivo', 'nube')),
  respuesta_usuario  text not null default 'sin_respuesta'
                       check (respuesta_usuario in ('aceptada', 'ajustada', 'rechazada', 'sin_respuesta')),
  feedback           text check (feedback in ('positivo', 'negativo')),
  payload            jsonb,
  client_id          uuid unique,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

create table public.voice_commands (
  id                uuid primary key default gen_random_uuid(),
  user_id           uuid not null default auth.uid() references auth.users (id) on delete cascade,
  texto_transcrito  text not null check (char_length(texto_transcrito) between 1 and 300),
  intencion         text not null check (intencion in ('consultar_vida', 'usar_objeto', 'activar_escudo',
                                                        'consultar_monstruo', 'cambiar_limite', 'desconocida')),
  parametros        jsonb not null default '{}'::jsonb,
  confirmado        boolean not null default false,
  ejecutado         boolean not null default false,
  created_at        timestamptz not null default now(),
  check (not ejecutado or confirmado or intencion in ('consultar_vida', 'consultar_monstruo'))
);

-- Cuota de llamadas a la IA en la nube (la escriben las Edge Functions con service_role).
create table public.ai_usage_quota (
  user_id   uuid not null references auth.users (id) on delete cascade,
  fecha     date not null default current_date,
  funcion   text not null check (funcion in ('generate-daily-summary', 'generate-weekly-boss', 'suggest-limits', 'coach-message')),
  llamadas  int  not null default 0 check (llamadas >= 0),
  primary key (user_id, fecha, funcion)
);

-- ---------------------------------------------------------------------------------
-- 5. Índices (consultas por usuario y fecha)
-- ---------------------------------------------------------------------------------
create index monsters_user_idx           on public.monsters (user_id);
create index daily_sessions_user_idx     on public.daily_sessions (user_id, fecha desc);
create index app_usage_user_fecha_idx    on public.app_usage (user_id, fecha desc, hora);
create index app_usage_monster_idx       on public.app_usage (monster_id);
create index item_events_user_idx        on public.item_events (user_id, created_at desc);
create index item_events_fecha_idx       on public.item_events (user_id, fecha, tipo_objeto);
create index item_events_monster_idx     on public.item_events (monster_id);
create index weekly_challenges_user_idx  on public.weekly_challenges (user_id, semana desc);
create index ai_insights_user_idx        on public.ai_insights (user_id, created_at desc);
create index ai_insights_tipo_idx        on public.ai_insights (user_id, tipo, created_at desc);
create index voice_commands_created_idx  on public.voice_commands (created_at);
create index voice_commands_user_idx     on public.voice_commands (user_id, created_at desc);
create index devices_user_idx            on public.devices (user_id);
create index user_achievements_ach_idx   on public.user_achievements (achievement_id);
create index user_avatar_items_item_idx  on public.user_avatar_items (item_id);
create index monsters_ilustracion_idx    on public.monsters (ilustracion_id);
create index profiles_avatar_idx         on public.profiles (avatar_id);
create index boss_catalog_recompensa_idx on public.boss_catalog (recompensa_id);
create index weekly_challenges_rec_idx   on public.weekly_challenges (recompensa_id);

-- ---------------------------------------------------------------------------------
-- 6. updated_at automático
-- ---------------------------------------------------------------------------------
create trigger profiles_updated_at          before update on public.profiles          for each row execute function public.set_updated_at();
create trigger monsters_updated_at          before update on public.monsters          for each row execute function public.set_updated_at();
create trigger daily_sessions_updated_at    before update on public.daily_sessions    for each row execute function public.set_updated_at();
create trigger app_usage_updated_at         before update on public.app_usage         for each row execute function public.set_updated_at();
create trigger inventory_updated_at         before update on public.inventory         for each row execute function public.set_updated_at();
create trigger weekly_challenges_updated_at before update on public.weekly_challenges for each row execute function public.set_updated_at();
create trigger ai_settings_updated_at       before update on public.ai_settings       for each row execute function public.set_updated_at();
create trigger ai_insights_updated_at       before update on public.ai_insights       for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------------
-- 7. Triggers de reglas
-- ---------------------------------------------------------------------------------

-- Inventario recalculado SIEMPRE a partir de item_events (resolución de conflictos
-- offline: el inventario no se sincroniza, se deduce del historial auditable).
create or replace function public.recompute_inventory()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_total int;
  v_max   int := case new.tipo_objeto when 'revivir' then 1 else 3 end;
begin
  select coalesce(sum(case accion when 'ganado' then 1 else -1 end), 0)
    into v_total
    from public.item_events
   where user_id = new.user_id and tipo_objeto = new.tipo_objeto;

  insert into public.inventory (user_id, tipo_objeto, cantidad)
  values (new.user_id, new.tipo_objeto, greatest(0, least(v_total, v_max)))
  on conflict (user_id, tipo_objeto) do update set cantidad = excluded.cantidad;
  return new;
end;
$$;

create trigger item_events_recompute
after insert on public.item_events
for each row execute function public.recompute_inventory();

-- Un día cerrado ya no se puede modificar desde la app.
create or replace function public.protect_closed_session()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if old.cerrado and current_setting('hp.closing_day', true) is distinct from 'on' then
    raise exception 'El día % ya está cerrado', old.fecha using errcode = 'check_violation';
  end if;
  return new;
end;
$$;

create trigger daily_sessions_protect
before update on public.daily_sessions
for each row execute function public.protect_closed_session();

-- Modo familiar: la IA en la nube queda desactivada SIEMPRE.
create or replace function public.enforce_family_mode()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.nube_permitida and exists (
       select 1 from public.profiles where id = new.user_id and modo_familiar) then
    new.nube_permitida := false;
  end if;
  return new;
end;
$$;

create trigger ai_settings_family
before insert or update on public.ai_settings
for each row execute function public.enforce_family_mode();

create or replace function public.family_mode_disables_cloud()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.modo_familiar and not old.modo_familiar then
    update public.ai_settings set nube_permitida = false where user_id = new.id;
  end if;
  return new;
end;
$$;

create trigger profiles_family
after update of modo_familiar on public.profiles
for each row execute function public.family_mode_disables_cloud();

-- Registro: crea perfil, ajustes de IA, objetos iniciales y estilo base.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, nombre_jugador)
  values (
    new.id,
    coalesce(nullif(left(trim(new.raw_user_meta_data ->> 'nombre_jugador'), 16), ''), 'Jugador')
  );
  insert into public.ai_settings (user_id) values (new.id);
  insert into public.item_events (user_id, tipo_objeto, accion, origen, motivo) values
    (new.id, 'pocion',  'ganado', 'sistema', 'bienvenida'),
    (new.id, 'escudo',  'ganado', 'sistema', 'bienvenida'),
    (new.id, 'revivir', 'ganado', 'sistema', 'bienvenida');
  insert into public.user_avatar_items (user_id, item_id, equipado) values (new.id, 'indigo', true);
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------------
-- 8. Row Level Security
-- ---------------------------------------------------------------------------------
alter table public.monster_catalog   enable row level security;
alter table public.achievements      enable row level security;
alter table public.avatar_items      enable row level security;
alter table public.boss_catalog      enable row level security;
alter table public.profiles          enable row level security;
alter table public.monsters          enable row level security;
alter table public.daily_sessions    enable row level security;
alter table public.app_usage         enable row level security;
alter table public.inventory         enable row level security;
alter table public.item_events       enable row level security;
alter table public.user_achievements enable row level security;
alter table public.user_avatar_items enable row level security;
alter table public.weekly_challenges enable row level security;
alter table public.devices           enable row level security;
alter table public.ai_settings       enable row level security;
alter table public.ai_insights       enable row level security;
alter table public.voice_commands    enable row level security;
alter table public.ai_usage_quota    enable row level security;

-- Catálogos: lectura para todos.
create policy "catálogo: lectura" on public.monster_catalog for select to anon, authenticated using (true);
create policy "catálogo: lectura" on public.achievements    for select to anon, authenticated using (true);
create policy "catálogo: lectura" on public.avatar_items    for select to anon, authenticated using (true);
create policy "catálogo: lectura" on public.boss_catalog    for select to anon, authenticated using (true);

-- Perfil: la clave es "id", no "user_id".
create policy "perfil: dueño lee"     on public.profiles for select to authenticated using (id = (select auth.uid()));
create policy "perfil: dueño edita"   on public.profiles for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

-- Tablas con user_id: el dueño puede leer, crear, editar y borrar (los permisos por
-- columna del apartado 9 limitan QUÉ puede escribir).
do $$
declare
  t text;
begin
  foreach t in array array['monsters', 'daily_sessions', 'app_usage', 'devices', 'ai_insights', 'voice_commands', 'user_avatar_items', 'ai_settings']
  loop
    execute format('create policy "dueño: lee" on public.%I for select to authenticated using (user_id = (select auth.uid()))', t);
    execute format('create policy "dueño: crea" on public.%I for insert to authenticated with check (user_id = (select auth.uid()))', t);
    execute format('create policy "dueño: edita" on public.%I for update to authenticated using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()))', t);
    execute format('create policy "dueño: borra" on public.%I for delete to authenticated using (user_id = (select auth.uid()))', t);
  end loop;
  -- Solo lectura para la app (se escriben con funciones):
  foreach t in array array['inventory', 'item_events', 'user_achievements', 'weekly_challenges', 'ai_usage_quota']
  loop
    execute format('create policy "dueño: lee" on public.%I for select to authenticated using (user_id = (select auth.uid()))', t);
  end loop;
end;
$$;

-- ---------------------------------------------------------------------------------
-- 9. Permisos por columna (anti-trampas)
-- ---------------------------------------------------------------------------------
revoke all on all tables in schema public from anon, authenticated;

grant select on public.monster_catalog, public.achievements, public.avatar_items, public.boss_catalog to anon, authenticated;

grant select on public.profiles to authenticated;
grant update (nombre_jugador, avatar_id, vida_maxima, bonus_fin_semana, zona_horaria, tono_coach, modo_familiar, consentimiento_nube_en)
  on public.profiles to authenticated;                       -- NO xp, nivel, racha, titulo, pin_hash, revivir

grant select, delete on public.monsters to authenticated;
grant update (nombre_app, nombre_monstruo, ilustracion_id, es_aliado, activo) on public.monsters to authenticated;
grant insert (package_name, nombre_app, nombre_monstruo, ilustracion_id, limite_minutos, es_aliado, activo, user_id)
  on public.monsters to authenticated;                       -- límite inicial al crear; cambios con set_monster_limit()

grant select on public.daily_sessions to authenticated;
grant insert (user_id, fecha, hp_inicial, hp_final, hp_regenerado, pociones_usadas, game_over, hora_game_over)
  on public.daily_sessions to authenticated;
grant update (hp_final, hp_regenerado, pociones_usadas, game_over, hora_game_over)
  on public.daily_sessions to authenticated;                 -- NO xp_ganada ni cerrado

grant select, insert, delete on public.app_usage to authenticated;
grant update (minutos, monster_id, es_aliado) on public.app_usage to authenticated;

grant select on public.inventory, public.item_events, public.user_achievements, public.weekly_challenges, public.ai_usage_quota
  to authenticated;

grant select, insert, delete on public.user_avatar_items to authenticated;
grant update (equipado) on public.user_avatar_items to authenticated;

grant select, delete on public.devices to authenticated;
grant insert (user_id, tipo, modelo, ultima_conexion) on public.devices to authenticated;
grant update (ultima_conexion) on public.devices to authenticated;

grant select on public.ai_settings to authenticated;
grant update (oraculo_activo, deteccion_compulsiva_activa, coach_activo, voz_activa, resumenes_activos, jefes_ia_activos,
              limites_activos, contexto_activo, tono_coach, frecuencia_coach_minutos, nube_permitida, nube_consentida_en,
              aprendizaje_iniciado_en)
  on public.ai_settings to authenticated;

grant select, insert, delete on public.ai_insights to authenticated;
grant update (respuesta_usuario, feedback) on public.ai_insights to authenticated;

grant select, insert, delete on public.voice_commands to authenticated;
grant update (confirmado, ejecutado) on public.voice_commands to authenticated;

-- =====================================================================================
-- HP: Health Points — 003 · Funciones RPC (acciones sensibles, anti-trampas)
--
-- Todas: security definer + search_path vacío + comprueban auth.uid().
-- Las que la app sincroniza sin conexión aceptan un client_id (uuid) para que reintentar
-- no duplique la acción (idempotencia).
-- =====================================================================================

-- ---------------------------------------------------------------------------------
-- Progresión (misma curva que Progression.kt)
-- ---------------------------------------------------------------------------------
create or replace function public.xp_para_nivel(p_nivel int)
returns int
language sql
immutable
set search_path = ''
as $$
  select 40 * (least(greatest(p_nivel, 1), 50) - 1) + 4 * (least(greatest(p_nivel, 1), 50) - 1) ^ 2;
$$;

create or replace function public.nivel_para_xp(p_xp int)
returns int
language sql
immutable
set search_path = ''
as $$
  select coalesce(max(n), 1) from generate_series(1, 50) n where public.xp_para_nivel(n) <= p_xp;
$$;

create or replace function public.titulo_para_nivel(p_nivel int)
returns text
language sql
immutable
set search_path = ''
as $$
  select case
    when p_nivel >= 40 then 'Leyenda Zen'
    when p_nivel >= 25 then 'Maestro del Enfoque'
    when p_nivel >= 12 then 'Guerrero Offline'
    when p_nivel >= 5  then 'Aprendiz Desconectado'
    else 'Novato del Scroll'
  end;
$$;

-- Interna (no expuesta): desbloquea un logro una sola vez. Devuelve true si es nuevo.
create or replace function public._unlock(p_user uuid, p_achievement text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.user_achievements (user_id, achievement_id) values (p_user, p_achievement)
  on conflict do nothing;
  return found;
end;
$$;

-- Interna: da un objeto si no se supera el máximo del inventario.
create or replace function public._grant_item(p_user uuid, p_tipo text, p_motivo text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_actual int;
  v_max int := case p_tipo when 'revivir' then 1 else 3 end;
begin
  select cantidad into v_actual from public.inventory where user_id = p_user and tipo_objeto = p_tipo;
  if coalesce(v_actual, 0) >= v_max then
    return false;
  end if;
  insert into public.item_events (user_id, tipo_objeto, accion, origen, motivo)
  values (p_user, p_tipo, 'ganado', 'sistema', p_motivo);
  return true;
end;
$$;

-- ---------------------------------------------------------------------------------
-- PIN (para Revivir): se guarda solo el hash bcrypt.
-- ---------------------------------------------------------------------------------
create or replace function public.set_pin(p_pin text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then raise exception 'Se requiere sesión' using errcode = '28000'; end if;
  if p_pin !~ '^[0-9]{4}$' then raise exception 'El PIN debe tener 4 dígitos' using errcode = '22023'; end if;
  update public.profiles set pin_hash = extensions.crypt(p_pin, extensions.gen_salt('bf')) where id = auth.uid();
end;
$$;

-- ---------------------------------------------------------------------------------
-- Usar un objeto (poción o escudo). Origen: reloj / teléfono / voz.
-- Respuesta: {"ok": bool, "motivo": text, "cantidad": int}
-- ---------------------------------------------------------------------------------
create or replace function public.use_item(
  p_tipo       text,
  p_origen     text,
  p_fecha      date,                  -- día de juego (el reloj/teléfono pueden sincronizar tarde)
  p_client_id  uuid,
  p_monster_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_cantidad int;
begin
  if v_user is null then raise exception 'Se requiere sesión' using errcode = '28000'; end if;
  if p_tipo not in ('pocion', 'escudo') then
    return jsonb_build_object('ok', false, 'motivo', 'objeto_no_valido');
  end if;
  if p_origen not in ('reloj', 'telefono', 'voz') then
    return jsonb_build_object('ok', false, 'motivo', 'origen_no_valido');
  end if;
  if p_monster_id is not null and not exists (select 1 from public.monsters where id = p_monster_id and user_id = v_user) then
    return jsonb_build_object('ok', false, 'motivo', 'monstruo_no_valido');
  end if;

  -- Idempotencia: el mismo uso sincronizado dos veces cuenta una vez.
  if p_client_id is not null and exists (select 1 from public.item_events where client_id = p_client_id and user_id = v_user) then
    select cantidad into v_cantidad from public.inventory where user_id = v_user and tipo_objeto = p_tipo;
    return jsonb_build_object('ok', true, 'motivo', 'ya_registrado', 'cantidad', coalesce(v_cantidad, 0));
  end if;

  select cantidad into v_cantidad from public.inventory where user_id = v_user and tipo_objeto = p_tipo for update;
  if coalesce(v_cantidad, 0) <= 0 then
    return jsonb_build_object('ok', false, 'motivo', 'sin_objetos', 'cantidad', 0);
  end if;

  insert into public.item_events (user_id, tipo_objeto, accion, origen, monster_id, fecha, client_id)
  values (v_user, p_tipo, 'usado', p_origen, p_monster_id, p_fecha, p_client_id);

  perform public._unlock(v_user, case p_tipo when 'pocion' then 'first_potion' else 'first_shield' end);

  select cantidad into v_cantidad from public.inventory where user_id = v_user and tipo_objeto = p_tipo;
  return jsonb_build_object('ok', true, 'motivo', 'usado', 'cantidad', v_cantidad);
end;
$$;

-- ---------------------------------------------------------------------------------
-- Revivir: solo en Game Over, 1 vez por semana y con el PIN correcto. Requiere conexión.
-- Respuesta: {"ok": bool, "motivo": text, "hp": int, "disponible_en": timestamptz}
-- ---------------------------------------------------------------------------------
create or replace function public.revive(p_pin text, p_fecha date, p_client_id uuid default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_profile public.profiles;
begin
  if v_user is null then raise exception 'Se requiere sesión' using errcode = '28000'; end if;
  select * into v_profile from public.profiles where id = v_user for update;

  if v_profile.pin_hash is null then
    return jsonb_build_object('ok', false, 'motivo', 'sin_pin');
  end if;
  if v_profile.pin_hash <> extensions.crypt(coalesce(p_pin, ''), v_profile.pin_hash) then
    return jsonb_build_object('ok', false, 'motivo', 'pin_incorrecto');
  end if;
  if v_profile.revivir_disponible_en > now() then
    return jsonb_build_object('ok', false, 'motivo', 'no_disponible', 'disponible_en', v_profile.revivir_disponible_en);
  end if;
  if not exists (select 1 from public.daily_sessions
                  where user_id = v_user and fecha = p_fecha and game_over and not cerrado) then
    return jsonb_build_object('ok', false, 'motivo', 'no_es_game_over');
  end if;

  update public.profiles set revivir_disponible_en = now() + interval '7 days' where id = v_user;
  insert into public.item_events (user_id, tipo_objeto, accion, origen, motivo, fecha, client_id)
  values (v_user, 'revivir', 'usado', 'reloj', 'game_over', p_fecha, p_client_id);
  -- El revivir de la semana siguiente se repone al cerrar el día cuando esté disponible.
  return jsonb_build_object('ok', true, 'motivo', 'revivido', 'hp', 15,
                            'disponible_en', now() + interval '7 days');
end;
$$;

-- ---------------------------------------------------------------------------------
-- Cambiar el límite de un monstruo (voz, reloj o teléfono). Pasos de 5 min, 5–600.
-- ---------------------------------------------------------------------------------
create or replace function public.set_monster_limit(p_monster_id uuid, p_minutos int)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_limite int := least(600, greatest(5, (p_minutos / 5) * 5));
begin
  if v_user is null then raise exception 'Se requiere sesión' using errcode = '28000'; end if;
  update public.monsters set limite_minutos = v_limite
   where id = p_monster_id and user_id = v_user;
  if not found then
    return jsonb_build_object('ok', false, 'motivo', 'monstruo_no_valido');
  end if;
  return jsonb_build_object('ok', true, 'limite_minutos', v_limite);
end;
$$;

-- ---------------------------------------------------------------------------------
-- Cerrar el día (respawn): el SERVIDOR calcula XP, nivel, racha, recompensas, logros y
-- progreso del jefe. El HP final que manda la app se acota con lo que permite el uso
-- registrado (anti-trampas): no se puede declarar más vida de la posible.
-- Respuesta: {"xp_ganada", "nivel", "subio_nivel", "racha", "logros": [..], "objetos": [..]}
-- ---------------------------------------------------------------------------------
create or replace function public.close_day(p_fecha date)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user       uuid := auth.uid();
  v_session    public.daily_sessions;
  v_profile    public.profiles;
  v_usado      int;
  v_pociones   int;
  v_revivio    boolean;
  v_max_posible int;
  v_hp_final   int;
  v_xp         int;
  v_nivel_antes int;
  v_nivel      int;
  v_racha      int;
  v_logros     text[] := '{}';
  v_objetos    text[] := '{}';
  v_week       public.weekly_challenges;
  v_progreso   int;
begin
  if v_user is null then raise exception 'Se requiere sesión' using errcode = '28000'; end if;

  select * into v_session from public.daily_sessions where user_id = v_user and fecha = p_fecha for update;
  if not found then
    return jsonb_build_object('ok', false, 'motivo', 'sin_sesion');
  end if;
  if v_session.cerrado then
    return jsonb_build_object('ok', true, 'motivo', 'ya_cerrado', 'xp_ganada', v_session.xp_ganada);
  end if;

  -- Vida máxima posible según los datos del servidor.
  select coalesce(sum(minutos), 0) into v_usado
    from public.app_usage where user_id = v_user and fecha = p_fecha and not es_aliado;
  select count(*) into v_pociones
    from public.item_events where user_id = v_user and tipo_objeto = 'pocion' and accion = 'usado' and fecha = p_fecha;
  select exists (select 1 from public.item_events where user_id = v_user and tipo_objeto = 'revivir'
                  and accion = 'usado' and fecha = p_fecha)
    into v_revivio;

  v_max_posible := least(v_session.hp_inicial,
                         greatest(0, v_session.hp_inicial - v_usado + v_session.hp_regenerado
                                     + 10 * v_pociones + case when v_revivio then 15 else 0 end));
  v_hp_final := least(v_session.hp_final, v_max_posible);
  v_xp := case when v_session.game_over and v_hp_final = 0 then 0 else v_hp_final end;

  perform set_config('hp.closing_day', 'on', true);
  update public.daily_sessions
     set hp_final = v_hp_final, xp_ganada = v_xp, cerrado = true,
         game_over = v_session.game_over and v_hp_final = 0
   where id = v_session.id;

  -- Perfil: XP, nivel, título y racha.
  select * into v_profile from public.profiles where id = v_user for update;
  v_nivel_antes := v_profile.nivel;
  v_nivel := public.nivel_para_xp(v_profile.xp + v_xp);
  v_racha := case when v_hp_final = 0 then 0 else v_profile.racha_actual + 1 end;

  update public.profiles
     set xp = v_profile.xp + v_xp,
         nivel = v_nivel,
         titulo = public.titulo_para_nivel(v_nivel),
         racha_actual = v_racha,
         racha_maxima = greatest(v_profile.racha_maxima, v_racha)
   where id = v_user;

  -- Recompensas por racha.
  if v_racha > 0 and v_racha % 3 = 0 and public._grant_item(v_user, 'pocion', 'racha_' || v_racha) then
    v_objetos := array_append(v_objetos, 'pocion');
  end if;
  if v_racha > 0 and v_racha % 7 = 0 and public._grant_item(v_user, 'escudo', 'racha_' || v_racha) then
    v_objetos := array_append(v_objetos, 'escudo');
  end if;
  if v_profile.revivir_disponible_en <= now() and public._grant_item(v_user, 'revivir', 'semanal') then
    v_objetos := array_append(v_objetos, 'revivir');
  end if;

  -- Logros verificables en el servidor.
  if v_hp_final > 0 and public._unlock(v_user, 'first_day') then v_logros := array_append(v_logros, 'first_day'); end if;
  if v_racha >= 3  and public._unlock(v_user, 'streak_3')  then v_logros := array_append(v_logros, 'streak_3');  end if;
  if v_racha >= 7  and public._unlock(v_user, 'streak_7')  then v_logros := array_append(v_logros, 'streak_7');  end if;
  if v_racha >= 30 and public._unlock(v_user, 'streak_30') then v_logros := array_append(v_logros, 'streak_30'); end if;
  if v_hp_final * 2 > v_session.hp_inicial and public._unlock(v_user, 'half_life') then v_logros := array_append(v_logros, 'half_life'); end if;
  if v_session.hp_regenerado >= 20 and public._unlock(v_user, 'regen_max') then v_logros := array_append(v_logros, 'regen_max'); end if;
  if v_revivio and v_hp_final > 0 and public._unlock(v_user, 'comeback') then v_logros := array_append(v_logros, 'comeback'); end if;
  if v_nivel >= 10 and public._unlock(v_user, 'level_10') then v_logros := array_append(v_logros, 'level_10'); end if;
  if v_pociones = 0 and v_hp_final > 0 and exists (
       select 1 from public.monsters m
        where m.user_id = v_user and m.ilustracion_id = 'scrollzilla' and not m.es_aliado
          and coalesce((select sum(u.minutos) from public.app_usage u
                         where u.monster_id = m.id and u.fecha = p_fecha), 0) < m.limite_minutos)
     and public._unlock(v_user, 'no_potions_scrollzilla') then
    v_logros := array_append(v_logros, 'no_potions_scrollzilla');
  end if;

  -- Jefe de la semana: actualiza el progreso según su métrica.
  select * into v_week from public.weekly_challenges
   where user_id = v_user and semana = date_trunc('week', p_fecha)::date and estado = 'activo'
   for update;
  if found then
    v_progreso := case v_week.meta
      when 'minutos_redes'  then (select coalesce(sum(u.minutos), 0) from public.app_usage u
                                   join public.monsters m on m.id = u.monster_id
                                   join public.monster_catalog c on c.id = m.ilustracion_id
                                  where u.user_id = v_user and u.fecha >= v_week.semana and c.categoria = 'social')
      when 'minutos_video'  then (select coalesce(sum(u.minutos), 0) from public.app_usage u
                                   join public.monsters m on m.id = u.monster_id
                                   join public.monster_catalog c on c.id = m.ilustracion_id
                                  where u.user_id = v_user and u.fecha >= v_week.semana and c.categoria = 'video')
      when 'minutos_juegos' then (select coalesce(sum(u.minutos), 0) from public.app_usage u
                                   join public.monsters m on m.id = u.monster_id
                                   join public.monster_catalog c on c.id = m.ilustracion_id
                                  where u.user_id = v_user and u.fecha >= v_week.semana and c.categoria = 'juegos')
      when 'minutos_monstruos' then (select coalesce(sum(u.minutos), 0) from public.app_usage u
                                  where u.user_id = v_user and u.fecha >= v_week.semana and u.monster_id is not null)
      when 'noches_sin_uso' then (select count(distinct d.fecha) from public.daily_sessions d
                                  where d.user_id = v_user and d.fecha >= v_week.semana and d.fecha <= p_fecha
                                    and not exists (select 1 from public.app_usage u
                                                     where u.user_id = v_user and u.fecha = d.fecha
                                                       and u.hora >= 23 and not u.es_aliado))
      when 'dias_sin_game_over' then (select count(*) from public.daily_sessions d
                                  where d.user_id = v_user and d.fecha >= v_week.semana and d.cerrado and d.hp_final > 0)
      else v_week.progreso
    end;
    -- Metas "de presupuesto" (minutos_*): se falla al superar el objetivo; metas "de conteo": se supera al alcanzarlo.
    update public.weekly_challenges
       set progreso = v_progreso,
           estado = case
             when v_week.meta like 'minutos_%' and v_progreso > v_week.objetivo then 'fallido'
             when v_week.meta not like 'minutos_%' and v_progreso >= v_week.objetivo then 'superado'
             when v_week.meta like 'minutos_%' and p_fecha >= v_week.semana + 6 then 'superado'
             when p_fecha >= v_week.semana + 6 then 'fallido'
             else 'activo'
           end
     where id = v_week.id
    returning * into v_week;
    if v_week.estado = 'superado' then
      if public._unlock(v_user, 'boss_slayer') then v_logros := array_append(v_logros, 'boss_slayer'); end if;
      if v_week.recompensa_id is not null then
        insert into public.user_avatar_items (user_id, item_id) values (v_user, v_week.recompensa_id) on conflict do nothing;
      end if;
    end if;
  end if;

  return jsonb_build_object(
    'ok', true,
    'xp_ganada', v_xp,
    'hp_final', v_hp_final,
    'nivel', v_nivel,
    'subio_nivel', v_nivel > v_nivel_antes,
    'racha', v_racha,
    'logros', to_jsonb(v_logros),
    'objetos', to_jsonb(v_objetos)
  );
end;
$$;

-- ---------------------------------------------------------------------------------
-- Jefe de la semana desde el catálogo (respaldo cuando la IA en la nube no está activa).
-- Dificultad adaptativa: si el anterior se falló, el siguiente es más fácil.
-- ---------------------------------------------------------------------------------
create or replace function public.start_week(p_semana date)
returns public.weekly_challenges
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_semana date := date_trunc('week', p_semana)::date;
  v_prev public.weekly_challenges;
  v_dificultad int := 3;
  v_boss public.boss_catalog;
  v_result public.weekly_challenges;
begin
  if v_user is null then raise exception 'Se requiere sesión' using errcode = '28000'; end if;

  select * into v_result from public.weekly_challenges where user_id = v_user and semana = v_semana;
  if found then return v_result; end if;

  select * into v_prev from public.weekly_challenges
   where user_id = v_user and semana < v_semana order by semana desc limit 1;
  if found then
    v_dificultad := case v_prev.estado
      when 'fallido'  then greatest(1, v_prev.dificultad - 1)
      when 'superado' then least(5, v_prev.dificultad + 1)
      else v_prev.dificultad
    end;
  end if;

  select * into v_boss from public.boss_catalog
   order by abs(dificultad - v_dificultad), (hashtext(id || v_semana::text)) limit 1;

  insert into public.weekly_challenges (user_id, semana, nombre_jefe, descripcion, meta, objetivo, dificultad, recompensa_id)
  values (v_user, v_semana, v_boss.nombre, v_boss.descripcion, v_boss.meta, v_boss.objetivo, v_boss.dificultad, v_boss.recompensa_id)
  returning * into v_result;
  return v_result;
end;
$$;

-- ---------------------------------------------------------------------------------
-- Privacidad: exportar todos los datos y borrar la cuenta.
-- ---------------------------------------------------------------------------------
create or replace function public.export_my_data()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
begin
  if v_user is null then raise exception 'Se requiere sesión' using errcode = '28000'; end if;
  return jsonb_build_object(
    'exportado_en', now(),
    'perfil',            (select to_jsonb(p) - 'pin_hash' from public.profiles p where p.id = v_user),
    'monstruos',         coalesce((select jsonb_agg(m) from public.monsters m where m.user_id = v_user), '[]'),
    'dias',              coalesce((select jsonb_agg(d order by d.fecha) from public.daily_sessions d where d.user_id = v_user), '[]'),
    'uso_por_hora',      coalesce((select jsonb_agg(u order by u.fecha, u.hora) from public.app_usage u where u.user_id = v_user), '[]'),
    'inventario',        coalesce((select jsonb_agg(i) from public.inventory i where i.user_id = v_user), '[]'),
    'eventos_objetos',   coalesce((select jsonb_agg(e order by e.created_at) from public.item_events e where e.user_id = v_user), '[]'),
    'logros',            coalesce((select jsonb_agg(a) from public.user_achievements a where a.user_id = v_user), '[]'),
    'accesorios',        coalesce((select jsonb_agg(x) from public.user_avatar_items x where x.user_id = v_user), '[]'),
    'retos',             coalesce((select jsonb_agg(w order by w.semana) from public.weekly_challenges w where w.user_id = v_user), '[]'),
    'dispositivos',      coalesce((select jsonb_agg(v) from public.devices v where v.user_id = v_user), '[]'),
    'ia_ajustes',        (select to_jsonb(s) from public.ai_settings s where s.user_id = v_user),
    'ia_resultados',     coalesce((select jsonb_agg(i order by i.created_at) from public.ai_insights i where i.user_id = v_user), '[]'),
    'ia_comandos_voz',   coalesce((select jsonb_agg(c order by c.created_at) from public.voice_commands c where c.user_id = v_user), '[]')
  );
end;
$$;

-- Borra al usuario de auth.users: todas sus filas se eliminan en cascada.
-- Los archivos del bucket "avatars" los borra antes la app con la API de Storage
-- (Supabase no permite borrar objetos de Storage con SQL).
create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
begin
  if v_user is null then raise exception 'Se requiere sesión' using errcode = '28000'; end if;
  delete from auth.users where id = v_user;
end;
$$;

-- Borrar solo los datos de IA (Configuración de IA → "Borrar datos de IA").
create or replace function public.delete_my_ai_data()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then raise exception 'Se requiere sesión' using errcode = '28000'; end if;
  delete from public.ai_insights where user_id = auth.uid();
  delete from public.voice_commands where user_id = auth.uid();
  update public.ai_settings set aprendizaje_iniciado_en = now() where user_id = auth.uid();
end;
$$;

-- ---------------------------------------------------------------------------------
-- Permisos de ejecución
-- ---------------------------------------------------------------------------------
revoke all on function public._unlock(uuid, text)                  from public, anon, authenticated;
revoke all on function public._grant_item(uuid, text, text)        from public, anon, authenticated;
revoke all on function public.set_pin(text)                        from public, anon;
revoke all on function public.use_item(text, text, date, uuid, uuid)     from public, anon;
revoke all on function public.revive(text, date, uuid)             from public, anon;
revoke all on function public.set_monster_limit(uuid, int)         from public, anon;
revoke all on function public.close_day(date)                      from public, anon;
revoke all on function public.start_week(date)                     from public, anon;
revoke all on function public.export_my_data()                     from public, anon;
revoke all on function public.delete_my_account()                  from public, anon;
revoke all on function public.delete_my_ai_data()                  from public, anon;
revoke all on function public.recompute_inventory()                from public, anon, authenticated;
revoke all on function public.handle_new_user()                    from public, anon, authenticated;
revoke all on function public.enforce_family_mode()                from public, anon, authenticated;
revoke all on function public.family_mode_disables_cloud()         from public, anon, authenticated;

grant execute on function public.set_pin(text)                    to authenticated;
grant execute on function public.use_item(text, text, date, uuid, uuid) to authenticated;
grant execute on function public.revive(text, date, uuid)         to authenticated;
grant execute on function public.set_monster_limit(uuid, int)     to authenticated;
grant execute on function public.close_day(date)                  to authenticated;
grant execute on function public.start_week(date)                 to authenticated;
grant execute on function public.export_my_data()                 to authenticated;
grant execute on function public.delete_my_account()              to authenticated;
grant execute on function public.delete_my_ai_data()              to authenticated;
grant execute on function public.xp_para_nivel(int), public.nivel_para_xp(int), public.titulo_para_nivel(int)
  to anon, authenticated;

-- =====================================================================================
-- HP: Health Points — 004 · Datos semilla de los catálogos
-- (se puede ejecutar varias veces: on conflict do update)
-- =====================================================================================

insert into public.avatar_items (id, nombre, tipo, nivel_requerido, imagen_path) values
  ('indigo',  'Índigo', 'estilo',    1,  'avatar/estilo_indigo.png'),
  ('lila',    'Lila',   'estilo',    1,  'avatar/estilo_lila.png'),
  ('coral',   'Coral',  'estilo',    1,  'avatar/estilo_coral.png'),
  ('arena',   'Arena',  'estilo',    1,  'avatar/estilo_arena.png'),
  ('gorro',   'Gorro',  'accesorio', 3,  'avatar/acc_gorro.png'),
  ('capa',    'Capa',   'accesorio', 8,  'avatar/acc_capa.png'),
  ('espada',  'Espada', 'accesorio', 15, 'avatar/acc_espada.png'),
  ('aura',    'Aura',   'accesorio', 25, 'avatar/acc_aura.png')
on conflict (id) do update set
  nombre = excluded.nombre, tipo = excluded.tipo,
  nivel_requerido = excluded.nivel_requerido, imagen_path = excluded.imagen_path;

insert into public.monster_catalog (id, nombre, descripcion, ilustracion_path, categoria, feroz, limite_sugerido, paquetes_sugeridos) values
  ('scrollzilla', 'Scrollzilla', 'Dinosaurio bizco de tanto mirar la pantalla. Nunca se cansa de deslizar.',
     'monstruos/scrollzilla.png', 'social', true, 30,
     array['com.zhiliaoapp.musically', 'com.ss.android.ugc.trill']),
  ('medusa', 'La Medusa del Feed', 'Medusa hipnótica: sus tentáculos atrapan el dedo en el feed infinito.',
     'monstruos/medusa.png', 'social', true, 40,
     array['com.instagram.android', 'com.facebook.katana', 'com.twitter.android', 'com.reddit.frontpage']),
  ('rey_autoplay', 'Rey Autoplay', 'Rey de ojos entrecerrados que siempre tiene listo el siguiente video.',
     'monstruos/rey_autoplay.png', 'video', true, 45,
     array['com.google.android.youtube', 'com.netflix.mediaclient', 'tv.twitch.android.app']),
  ('goblin', 'Goblin del Grind', 'Goblin que nunca suelta el mando: "solo una partida más".',
     'monstruos/goblin.png', 'juegos', true, 30,
     array['com.supercell.clashroyale', 'com.supercell.brawlstars', 'com.roblox.client', 'com.mojang.minecraftpe']),
  ('fantasma', 'Fantasma del Chat', 'Fantasmita que aparece y desaparece con cada notificación.',
     'monstruos/fantasma.png', 'chat', false, 60,
     array['com.whatsapp', 'org.telegram.messenger', 'com.snapchat.android'])
on conflict (id) do update set
  nombre = excluded.nombre, descripcion = excluded.descripcion, ilustracion_path = excluded.ilustracion_path,
  categoria = excluded.categoria, feroz = excluded.feroz, limite_sugerido = excluded.limite_sugerido,
  paquetes_sugeridos = excluded.paquetes_sugeridos;

insert into public.achievements (id, titulo, descripcion, icono_path, orden) values
  ('first_day',              'Primer día sin Game Over', 'Termina un día con vida restante.',              'medallas/first_day.png', 1),
  ('half_life',              'Medio tanque',             'Termina un día con más del 50 % de vida.',       'medallas/half_life.png', 2),
  ('streak_3',               'Racha de 3',               '3 días seguidos sin Game Over.',                 'medallas/streak_3.png',  3),
  ('streak_7',               '7 días de racha',          'Una semana entera sin Game Over.',               'medallas/streak_7.png',  4),
  ('streak_30',              'Mes imparable',            '30 días seguidos sin Game Over.',                'medallas/streak_30.png', 5),
  ('first_potion',           'Alquimista',               'Usa tu primera poción.',                         'medallas/first_potion.png', 6),
  ('first_shield',           'Defensor',                 'Activa tu primer escudo.',                       'medallas/first_shield.png', 7),
  ('regen_max',              'Monje del reposo',         'Recupera los 20 HP de regeneración en un día.',  'medallas/regen_max.png', 8),
  ('no_potions_scrollzilla', 'Cazador puro',             'Mantén a Scrollzilla bajo su límite sin usar pociones.', 'medallas/cazador.png', 9),
  ('boss_slayer',            'Matajefes',                'Derrota a un Jefe Final semanal.',               'medallas/boss_slayer.png', 10),
  ('level_10',               'Doble dígito',             'Alcanza el nivel 10.',                           'medallas/level_10.png', 11),
  ('comeback',               'Fénix',                    'Revive y termina el día sin volver a caer.',     'medallas/comeback.png', 12)
on conflict (id) do update set
  titulo = excluded.titulo, descripcion = excluded.descripcion, icono_path = excluded.icono_path, orden = excluded.orden;

-- Jefes de respaldo (cuando la IA en la nube está desactivada). Metas reconocidas por close_day:
--   minutos_redes | minutos_video | minutos_juegos | minutos_monstruos  → presupuesto (no superar)
--   noches_sin_uso | dias_sin_game_over                                  → conteo (alcanzar)
insert into public.boss_catalog (id, nombre, descripcion, meta, objetivo, dificultad, recompensa_id, categoria) values
  ('hidra_social',     'Hidra Social',        'Usa redes sociales menos de 5 horas esta semana.',           'minutos_redes',      300, 3, 'aura',   'social'),
  ('hidra_menor',      'Hidra Menor',         'Usa redes sociales menos de 7 horas esta semana.',           'minutos_redes',      420, 1, 'gorro',  'social'),
  ('rey_maraton',      'Rey del Maratón',     'Menos de 4 horas de video esta semana.',                     'minutos_video',      240, 3, 'capa',   'video'),
  ('senor_grind',      'Señor del Grind',     'Menos de 3 horas de juegos esta semana.',                    'minutos_juegos',     180, 3, 'espada', 'juegos'),
  ('vampiro_nocturno', 'El Vampiro Nocturno', 'No uses el teléfono después de las 11 p. m. durante 5 noches.', 'noches_sin_uso',   5,   4, 'capa',   'noche'),
  ('murcielago',       'El Murciélago',       'No uses el teléfono después de las 11 p. m. durante 3 noches.', 'noches_sin_uso',   3,   2, 'gorro',  'noche'),
  ('gran_fantasma',    'Gran Fantasma',       'Menos de 8 horas en todos tus monstruos esta semana.',       'minutos_monstruos',  480, 2, 'gorro',  'general'),
  ('titan_constancia', 'Titán de la Constancia', 'Termina 6 días de esta semana sin Game Over.',            'dias_sin_game_over', 6,   5, 'aura',   'general')
on conflict (id) do update set
  nombre = excluded.nombre, descripcion = excluded.descripcion, meta = excluded.meta, objetivo = excluded.objetivo,
  dificultad = excluded.dificultad, recompensa_id = excluded.recompensa_id, categoria = excluded.categoria;

-- =====================================================================================
-- HP: Health Points — 005 · Storage, Realtime y retención de datos
-- =====================================================================================

-- ---------------------------------------------------------------------------------
-- Storage
--   avatars      privado, una carpeta por usuario: avatars/<user_id>/...
--   game-assets  público y de solo lectura (ilustraciones, medallas, accesorios)
-- ---------------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('avatars',     'avatars',     false, 1048576, array['image/png', 'image/webp']),
  ('game-assets', 'game-assets', true,  2097152, array['image/png', 'image/webp', 'image/svg+xml'])
on conflict (id) do update set
  public = excluded.public, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "avatars: dueño lee"   on storage.objects;
drop policy if exists "avatars: dueño sube"  on storage.objects;
drop policy if exists "avatars: dueño edita" on storage.objects;
drop policy if exists "avatars: dueño borra" on storage.objects;
drop policy if exists "game-assets: lectura" on storage.objects;

create policy "avatars: dueño lee" on storage.objects for select to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "avatars: dueño sube" on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "avatars: dueño edita" on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "avatars: dueño borra" on storage.objects for delete to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
-- game-assets: público para leer; nadie puede escribir desde la app (sin políticas de escritura).
create policy "game-assets: lectura" on storage.objects for select to anon, authenticated
  using (bucket_id = 'game-assets');

-- ---------------------------------------------------------------------------------
-- Realtime: cambios que el teléfono escucha y reenvía al reloj al instante.
-- ---------------------------------------------------------------------------------
do $$
declare
  t text;
begin
  foreach t in array array['inventory', 'profiles', 'user_achievements', 'weekly_challenges', 'ai_insights']
  loop
    begin
      execute format('alter publication supabase_realtime add table public.%I', t);
    exception when duplicate_object then
      null; -- ya estaba en la publicación
    end;
  end loop;
end;
$$;

-- ---------------------------------------------------------------------------------
-- Retención: los comandos de voz se borran a los 30 días (pg_cron, todos los días 3:15).
-- Si pg_cron no está habilitado, actívalo en Database → Extensions y vuelve a ejecutar.
-- ---------------------------------------------------------------------------------
create or replace function public.purge_old_voice_commands()
returns void
language sql
security definer
set search_path = ''
as $$
  delete from public.voice_commands where created_at < now() - interval '30 days';
  delete from public.ai_usage_quota where fecha < current_date - 7;
$$;
revoke all on function public.purge_old_voice_commands() from public, anon, authenticated;

do $$
begin
  create extension if not exists pg_cron;
  perform cron.schedule('hp-purge-voice', '15 3 * * *', 'select public.purge_old_voice_commands()');
exception when others then
  raise notice 'pg_cron no disponible: habilítalo en Database → Extensions para la retención automática (%).', sqlerrm;
end;
$$;
