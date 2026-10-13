# Supabase · HP: Health Points

| Carpeta | Contenido |
|---|---|
| `migrations/001_hp_cloud.sql` | Esquema de prueba inicial (reemplazado por la 002). |
| `migrations/002_schema.sql` | Tablas de juego e IA, índices, triggers, RLS y permisos por columna (anti-trampas). |
| `migrations/003_functions.sql` | Funciones RPC: `use_item`, `revive`, `set_pin`, `set_monster_limit`, `close_day`, `start_week`, `export_my_data`, `delete_my_account`, `delete_my_ai_data`. |
| `migrations/004_seed.sql` | Catálogos: monstruos, logros, accesorios y jefes. |
| `migrations/005_storage_realtime.sql` | Buckets `avatars` y `game-assets`, publicación Realtime y retención de 30 días (pg_cron). |
| `deploy/hp_schema_v2.sql` | 002–005 en un solo archivo para pegar en el SQL Editor. |
| `migrations/006_usage_upsert.sql` | Permite el upsert de `app_usage` y bloquea modificar el uso de un día ya cerrado (anti-trampas). Pegar después de v2. |
| `tests/` | Pruebas de RLS y reglas en un PostgreSQL local que imita a Supabase (`00_supabase_stub.sql`). |

## Probar en local

```bash
psql -c "create database hp"
psql -d hp -f tests/00_supabase_stub.sql
psql -d hp -f migrations/001_hp_cloud.sql -f deploy/hp_schema_v2.sql
psql -d hp -f tests/01_rls_and_rules.sql     # 21 líneas "OK", ningún "FALLO"
```
