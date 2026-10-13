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
