# Nube con Supabase

Supabase cumple dos papeles en HP:

1. **Respaldo**: el teléfono guarda en la nube el estado del día, el historial y los logros. Si cambias de
   teléfono o se borra la app, el progreso no se pierde.
2. **Sincronización del reloj por Wi‑Fi**: el reloj puede recibir la partida en tiempo real sin estar emparejado
   por Bluetooth (Wear OS). Usa los mismos mensajes que la Data Layer, así que las pantallas no cambian.

```
Teléfono ──upsert──▶ players / day_history / achievements (RLS: solo el dueño)
   │
   └──Realtime Broadcast "hp-<watch_token>"──▶ Reloj
        state · event · result  ◀── command (poción, escudo, límite, revivir) + latido cada 60 s
```

Room y DataStore siguen siendo la fuente local: **sin internet la app funciona igual** y vuelve a subir los
datos en la siguiente sincronización.

## Puesta en marcha

1. Crea un proyecto en [supabase.com](https://supabase.com).
2. **SQL Editor** → pega [`supabase/migrations/001_hp_cloud.sql`](../supabase/migrations/001_hp_cloud.sql) → **Run**.
3. **Authentication → Sign In / Providers** → activa **Allow anonymous sign-ins**.
4. **Project Settings → API** → copia la *Project URL* y la clave *anon public* (o *publishable*).
5. Añádelas a `local.properties` (este archivo **no** se sube a git):
   ```properties
   supabase.url=https://xxxxxxxx.supabase.co
   supabase.key=eyJhbGciOi...
   ```
6. Compila de nuevo. Sin estas dos líneas la app funciona sin nube y Configuración lo indica.

## Vincular un reloj por Wi‑Fi

1. Teléfono: **Configuración → Nube y reloj → Vincular reloj por Wi‑Fi**. Aparece un código de 6 dígitos válido 10 minutos.
2. Reloj (sin partida): **Vincular con código** → escribe el código en el teclado 3×4 → **OK**.
3. El reloj guarda un token secreto y desde ese momento recibe la partida en tiempo real.

Para cortar el acceso: **Desvincular relojes** genera un token nuevo y los relojes anteriores dejan de recibir datos.

## Seguridad (decisiones)

| Decisión | Por qué |
|---|---|
| El teléfono inicia sesión **anónima** | No se pide correo ni contraseña en el onboarding (menos fricción); la cuenta queda ligada al dispositivo. |
| **RLS** en todas las tablas (`user_id = auth.uid()`) | Cada usuario solo puede leer y escribir lo suyo, aunque la clave anon sea pública. |
| El reloj **no** inicia sesión: usa un `watch_token` | Un reloj no tiene teclado para escribir credenciales; el token se obtiene canjeando un código de un solo uso. |
| `link_codes` sin políticas; solo funciones `security definer` | Los códigos no se pueden listar ni leer; solo canjear. |
| Códigos de 6 dígitos que caducan en 10 min y se borran al usarse | Equilibrio entre facilidad (teclado del reloj) y seguridad. Para producción convendría limitar intentos por IP. |
| Nunca la clave `service_role` en la app | Esa clave salta RLS; solo debe usarse en un servidor. |
