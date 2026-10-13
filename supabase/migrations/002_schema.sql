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
