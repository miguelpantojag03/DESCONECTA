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
