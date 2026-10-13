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
