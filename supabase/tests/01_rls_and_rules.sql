-- =====================================================================================
-- Pruebas de seguridad (RLS + permisos) y reglas anti-trampas.
-- Cada bloque falla con "FALLO: …" si algo no se comporta como se espera.
-- Ejecutar sobre una base con 00_supabase_stub.sql + migraciones 002–005.
-- =====================================================================================
\set ON_ERROR_STOP 1
\set A '''aaaaaaaa-0000-0000-0000-00000000000a'''
\set B '''bbbbbbbb-0000-0000-0000-00000000000b'''

-- Registro de dos jugadores: el trigger crea perfil, ajustes de IA, inventario y estilo.
reset role;
insert into auth.users (id, email, raw_user_meta_data) values
  (:A, 'a@hp.test', '{"nombre_jugador": "Ana"}'),
  (:B, 'b@hp.test', '{}');

do $$ begin
  assert (select nombre_jugador from public.profiles where id = 'aaaaaaaa-0000-0000-0000-00000000000a') = 'Ana',
    'FALLO: el trigger no creó el perfil con el nombre del registro';
  assert (select count(*) from public.inventory where user_id = 'aaaaaaaa-0000-0000-0000-00000000000a') = 3,
    'FALLO: inventario inicial incompleto';
  assert (select nube_permitida from public.ai_settings where user_id = 'aaaaaaaa-0000-0000-0000-00000000000a') = false,
    'FALLO: la IA en la nube debe estar desactivada por defecto';
  raise notice 'OK 1 · registro crea perfil, ajustes de IA (nube desactivada) e inventario';
end $$;

-- ---------------------------------------------------------------------------------
-- B crea datos propios.
-- ---------------------------------------------------------------------------------
set role authenticated;
select set_config('request.jwt.claim.sub', 'bbbbbbbb-0000-0000-0000-00000000000b', false);
insert into public.monsters (package_name, nombre_app, nombre_monstruo, ilustracion_id, limite_minutos)
values ('com.instagram.android', 'Instagram', 'Medusa de B', 'medusa', 40);
insert into public.ai_insights (tipo, contenido, motivo, confianza, origen)
values ('mensaje_coach', 'Frase privada de B', 'Prueba', 'alta', 'reglas');

-- ---------------------------------------------------------------------------------
-- A no puede ver NADA de B.
-- ---------------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', 'aaaaaaaa-0000-0000-0000-00000000000a', false);
do $$ begin
  assert (select count(*) from public.monsters) = 0, 'FALLO: A ve monstruos de B';
  assert (select count(*) from public.ai_insights) = 0, 'FALLO: A ve resultados de IA de B';
  assert (select count(*) from public.profiles) = 1, 'FALLO: A ve perfiles ajenos';
  assert (select count(*) from public.inventory) = 3, 'FALLO: A ve inventario ajeno';
  update public.monsters set nombre_monstruo = 'hackeado';           -- no afecta filas de B
  assert (select count(*) from public.monsters) = 0, 'FALLO: A modificó algo ajeno';
  raise notice 'OK 2 · RLS: un usuario no puede leer ni modificar datos de otro';
end $$;

do $$ begin
  insert into public.monsters (user_id, package_name, nombre_app, nombre_monstruo)
  values ('bbbbbbbb-0000-0000-0000-00000000000b', 'com.x', 'X', 'Intruso');
  raise exception 'FALLO: A pudo crear filas a nombre de B';
exception when insufficient_privilege then
  raise notice 'OK 3 · RLS: no se pueden crear filas a nombre de otro usuario';
end $$;

-- ---------------------------------------------------------------------------------
-- Anti-trampas: columnas protegidas.
-- ---------------------------------------------------------------------------------
do $$ begin
  update public.profiles set xp = 99999;
  raise exception 'FALLO: A pudo escribir su XP';
exception when insufficient_privilege then
  raise notice 'OK 4 · no se puede escribir XP/nivel/racha directamente';
end $$;

do $$ begin
  insert into public.item_events (user_id, tipo_objeto, accion, origen)
  values ('aaaaaaaa-0000-0000-0000-00000000000a', 'pocion', 'ganado', 'telefono');
  raise exception 'FALLO: A pudo regalarse pociones';
exception when insufficient_privilege then
  raise notice 'OK 5 · no se pueden crear objetos insertando eventos';
end $$;

do $$ begin
  update public.inventory set cantidad = 3;
  raise exception 'FALLO: A pudo editar su inventario';
exception when insufficient_privilege then
  raise notice 'OK 6 · el inventario es de solo lectura';
end $$;

insert into public.monsters (package_name, nombre_app, nombre_monstruo, ilustracion_id, limite_minutos)
values ('com.zhiliaoapp.musically', 'TikTok', 'Scrollzilla', 'scrollzilla', 30);

do $$ begin
  update public.monsters set limite_minutos = 600;
  raise exception 'FALLO: el límite se cambió sin pasar por set_monster_limit';
exception when insufficient_privilege then
  raise notice 'OK 7 · los límites solo cambian con la función set_monster_limit';
end $$;

do $$
declare r jsonb; m uuid;
begin
  select id into m from public.monsters limit 1;
  r := public.set_monster_limit(m, 43);
  assert (r ->> 'limite_minutos')::int = 40, 'FALLO: el límite no se ajustó a pasos de 5';
  r := public.set_monster_limit(m, 9999);
  assert (r ->> 'limite_minutos')::int = 600, 'FALLO: el límite superó 600';
  perform public.set_monster_limit(m, 30);
  raise notice 'OK 8 · set_monster_limit: pasos de 5 min entre 5 y 600';
end $$;

-- ---------------------------------------------------------------------------------
-- Objetos: usar, quedarse sin, idempotencia offline.
-- ---------------------------------------------------------------------------------
do $$
declare r jsonb;
begin
  r := public.use_item('pocion', 'reloj', '2026-10-13', '11111111-1111-1111-1111-111111111111');
  assert (r ->> 'ok')::boolean and (r ->> 'cantidad')::int = 0, 'FALLO: usar poción ' || r::text;
  r := public.use_item('pocion', 'reloj', '2026-10-13', '11111111-1111-1111-1111-111111111111');
  assert r ->> 'motivo' = 'ya_registrado', 'FALLO: reintento duplicado ' || r::text;
  r := public.use_item('pocion', 'voz', '2026-10-13', '22222222-2222-2222-2222-222222222222');
  assert not (r ->> 'ok')::boolean and r ->> 'motivo' = 'sin_objetos', 'FALLO: usó una poción que no tenía ' || r::text;
  assert exists (select 1 from public.user_achievements where achievement_id = 'first_potion'), 'FALLO: logro de primera poción';
  raise notice 'OK 9 · use_item: descuenta, es idempotente al reintentar y no deja usar lo que no hay';
end $$;

-- ---------------------------------------------------------------------------------
-- Revivir: PIN, solo en Game Over, una vez por semana.
-- ---------------------------------------------------------------------------------
select public.set_pin('4321');
insert into public.daily_sessions (fecha, hp_inicial, hp_final, game_over, hora_game_over)
values ('2026-10-12', 120, 0, true, '2026-10-12 20:00+00');

do $$
declare r jsonb;
begin
  r := public.revive('0000', '2026-10-12');
  assert r ->> 'motivo' = 'pin_incorrecto', 'FALLO: revivió con PIN incorrecto ' || r::text;
  r := public.revive('4321', '2026-10-12');
  assert (r ->> 'ok')::boolean, 'FALLO: no revivió con el PIN correcto ' || r::text;
  r := public.revive('4321', '2026-10-12');
  assert r ->> 'motivo' = 'no_disponible', 'FALLO: revivió dos veces en la semana ' || r::text;
  raise notice 'OK 10 · revive: exige PIN, Game Over y respeta 1 vez por semana';
end $$;

-- ---------------------------------------------------------------------------------
-- Cerrar el día: el servidor acota la vida declarada con el uso registrado.
-- ---------------------------------------------------------------------------------
insert into public.daily_sessions (fecha, hp_inicial, hp_final, hp_regenerado)
values ('2026-10-13', 120, 120, 5);                              -- la app "dice" 120 HP…
insert into public.app_usage (package_name, fecha, hora, minutos) values
  ('com.zhiliaoapp.musically', '2026-10-13', 21, 60),
  ('com.zhiliaoapp.musically', '2026-10-13', 22, 40);              -- …pero usó 100 min

do $$
declare r jsonb;
begin
  r := public.close_day('2026-10-13');
  -- 120 − 100 + 5 de regeneración + 10 de la poción usada = 35 como máximo
  assert (r ->> 'hp_final')::int = 35, 'FALLO: el servidor no acotó la vida declarada ' || r::text;
  assert (r ->> 'xp_ganada')::int = 35, 'FALLO: XP mal calculada ' || r::text;
  assert (select xp from public.profiles) = 35, 'FALLO: XP no sumada al perfil';
  assert (select racha_actual from public.profiles) = 1, 'FALLO: racha';
  assert exists (select 1 from public.user_achievements where achievement_id = 'first_day'), 'FALLO: logro primer día';
  r := public.close_day('2026-10-13');
  assert r ->> 'motivo' = 'ya_cerrado', 'FALLO: se cerró dos veces (XP duplicada) ' || r::text;
  assert (select xp from public.profiles) = 35, 'FALLO: XP duplicada';
  raise notice 'OK 11 · close_day: acota la vida declarada, suma XP una sola vez y da logros';
end $$;

do $$ begin
  update public.daily_sessions set hp_final = 120 where fecha = '2026-10-13';
  raise exception 'FALLO: se editó un día cerrado';
exception when check_violation then
  raise notice 'OK 12 · un día cerrado no se puede modificar';
end $$;

-- ---------------------------------------------------------------------------------
-- IA: consentimiento, modo familiar y comandos de voz.
-- ---------------------------------------------------------------------------------
do $$ begin
  update public.ai_settings set nube_permitida = true;
  raise exception 'FALLO: se activó la nube sin consentimiento';
exception when check_violation then
  raise notice 'OK 13 · la IA en la nube exige consentimiento registrado';
end $$;

do $$ begin
  update public.ai_settings set frecuencia_coach_minutos = 10;
  raise exception 'FALLO: coach cada 10 min';
exception when check_violation then
  raise notice 'OK 14 · el coach no puede hablar más de una vez cada 45 min';
end $$;

update public.profiles set modo_familiar = true;
update public.ai_settings set nube_permitida = true, nube_consentida_en = now();
do $$ begin
  assert (select nube_permitida from public.ai_settings) = false, 'FALLO: modo familiar con IA en la nube';
  raise notice 'OK 15 · modo familiar: la IA en la nube queda desactivada siempre';
end $$;

do $$ begin
  insert into public.voice_commands (texto_transcrito, intencion, parametros, ejecutado)
  values ('usa una poción', 'usar_objeto', '{"objeto": "pocion"}', true);
  raise exception 'FALLO: se ejecutó un gasto por voz sin confirmación';
exception when check_violation then
  raise notice 'OK 16 · voz: gastar objetos o cambiar límites exige confirmación';
end $$;

do $$ begin
  insert into public.ai_insights (tipo, contenido, confianza, origen) values ('prediccion', 'x', 'segura', 'reglas');
  raise exception 'FALLO: confianza fuera del enum';
exception when check_violation then
  raise notice 'OK 17 · CHECK de enums en ai_insights';
end $$;

-- ---------------------------------------------------------------------------------
-- Jefe semanal, exportación y borrado.
-- ---------------------------------------------------------------------------------
do $$
declare w public.weekly_challenges; w2 public.weekly_challenges;
begin
  w := public.start_week('2026-10-14');
  assert w.semana = '2026-10-12', 'FALLO: la semana debe empezar en lunes';
  w2 := public.start_week('2026-10-15');
  assert w.id = w2.id, 'FALLO: dos jefes la misma semana';
  raise notice 'OK 18 · start_week: un jefe por semana desde el catálogo (%)', w.nombre_jefe;
end $$;

do $$
declare d jsonb;
begin
  d := public.export_my_data();
  assert d -> 'perfil' ? 'xp' and not (d -> 'perfil' ? 'pin_hash'), 'FALLO: exportación sin perfil o con el hash del PIN';
  assert jsonb_array_length(d -> 'monstruos') = 1, 'FALLO: exportación incompleta';
  raise notice 'OK 19 · export_my_data: incluye todo y nunca el hash del PIN';
end $$;

-- Sin sesión (anon): catálogos sí, datos de jugadores no.
reset role;
set role anon;
select set_config('request.jwt.claim.sub', '', false);
do $$ begin
  assert (select count(*) from public.achievements) = 12, 'FALLO: anon no ve el catálogo de logros';
  begin
    perform 1 from public.profiles;
    raise exception 'FALLO: anon leyó perfiles';
  exception when insufficient_privilege then null;
  end;
  raise notice 'OK 20 · anon: lee catálogos, no datos de jugadores';
end $$;

reset role;
set role authenticated;
select set_config('request.jwt.claim.sub', 'aaaaaaaa-0000-0000-0000-00000000000a', false);
select public.delete_my_account();
reset role;
do $$ begin
  assert not exists (select 1 from public.profiles where id = 'aaaaaaaa-0000-0000-0000-00000000000a'), 'FALLO: perfil no borrado';
  assert not exists (select 1 from public.item_events where user_id = 'aaaaaaaa-0000-0000-0000-00000000000a'), 'FALLO: eventos no borrados';
  assert not exists (select 1 from public.daily_sessions where user_id = 'aaaaaaaa-0000-0000-0000-00000000000a'), 'FALLO: días no borrados';
  assert exists (select 1 from public.monsters where user_id = 'bbbbbbbb-0000-0000-0000-00000000000b'), 'FALLO: se borraron datos de B';
  raise notice 'OK 21 · delete_my_account: borra en cascada solo los datos del usuario';
end $$;
