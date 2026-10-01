-- =====================================================================
-- SoundAlert: esquema inicial
--
-- users            propietario de uno o varios relojes (preparado para auth)
-- contexts         catálogo de contextos (HOME, STREET, UNIVERSITY, WORK)
-- devices          relojes / dispositivos y su contexto actual
-- device_settings  configuración 1:1 de cada dispositivo
-- sound_rules      reglas personalizadas por dispositivo + contexto + sonido
--                  (si no hay fila se aplica la regla por defecto del backend)
-- detections       cada sonido procesado (clasificación + decisión)
-- alerts           alertas generadas a partir de una detección
--
-- El backend accede con la service_role key (ignora RLS). RLS está activado
-- sin políticas para que las claves anon/authenticated no lean nada hasta
-- que se añada autenticación.
-- =====================================================================

create extension if not exists pgcrypto;

create type priority_level as enum ('DANGER', 'ATTENTION', 'INFORMATION');
create type device_platform as enum ('WEAR_OS', 'ANDROID', 'WATCH_OS', 'IOS', 'OTHER');
create type classification_source as enum ('SERVER', 'DEVICE');
create type detection_outcome as enum (
  'ALERTED', 'NO_PREDICTIONS', 'UNKNOWN_SOUND', 'LOW_CONFIDENCE',
  'DISABLED_IN_CONTEXT', 'COOLDOWN', 'ALERTS_DISABLED'
);
create type alert_status as enum ('ACTIVE', 'ACKNOWLEDGED');
-- Las categorías de sonido se guardan como text para poder añadir nuevas
-- (cuando se integre el modelo de IA) sin migrar un enum.

-- Mantiene updated_at al día en cada UPDATE.
create function set_updated_at() returns trigger
language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
create table users (
  id uuid primary key default gen_random_uuid(),
  -- Cuando se añada Supabase Auth: auth_user_id uuid unique references auth.users(id)
  name text not null check (char_length(name) between 1 and 80),
  email text unique check (email is null or position('@' in email) > 1),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create trigger users_updated_at before update on users
  for each row execute function set_updated_at();

-- ---------------------------------------------------------------------
-- contexts
-- ---------------------------------------------------------------------
create table contexts (
  code text primary key,
  name text not null,
  icon text not null,
  sort_order smallint not null default 0
);
insert into contexts (code, name, icon, sort_order) values
  ('HOME', 'Casa', 'home', 1),
  ('STREET', 'Calle', 'street', 2),
  ('UNIVERSITY', 'Universidad', 'school', 3),
  ('WORK', 'Trabajo', 'work', 4);

-- ---------------------------------------------------------------------
-- devices
-- ---------------------------------------------------------------------
create table devices (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid references users(id) on delete set null,
  name text not null check (char_length(name) between 1 and 80),
  platform device_platform not null default 'WEAR_OS',
  current_context text not null default 'HOME' references contexts(code),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index devices_owner_idx on devices (owner_id);
create trigger devices_updated_at before update on devices
  for each row execute function set_updated_at();

-- ---------------------------------------------------------------------
-- device_settings (1:1 con devices, se crea automáticamente)
-- ---------------------------------------------------------------------
create table device_settings (
  device_id uuid primary key references devices(id) on delete cascade,
  -- null = usar la confianza mínima global del servidor (MIN_CONFIDENCE)
  min_confidence numeric(4,3) check (min_confidence between 0 and 1),
  alerts_enabled boolean not null default true,
  updated_at timestamptz not null default now()
);
create trigger device_settings_updated_at before update on device_settings
  for each row execute function set_updated_at();

create function create_default_device_settings() returns trigger
language plpgsql as $$
begin
  insert into device_settings (device_id) values (new.id);
  return new;
end;
$$;
create trigger devices_default_settings after insert on devices
  for each row execute function create_default_device_settings();

-- ---------------------------------------------------------------------
-- sound_rules (personalizaciones por dispositivo)
-- ---------------------------------------------------------------------
create table sound_rules (
  device_id uuid not null references devices(id) on delete cascade,
  context text not null references contexts(code),
  category text not null,
  enabled boolean not null,
  priority priority_level not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (device_id, context, category)
);
create trigger sound_rules_updated_at before update on sound_rules
  for each row execute function set_updated_at();

-- ---------------------------------------------------------------------
-- detections
-- ---------------------------------------------------------------------
create table detections (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null references devices(id) on delete cascade,
  context text not null references contexts(code),
  source classification_source not null,
  classifier_name text not null,
  classifier_version text not null,
  -- [{ category, confidence, rawLabel }] tal como las devolvió el clasificador
  predictions jsonb not null default '[]'::jsonb,
  top_label text,
  top_category text,
  top_confidence numeric(4,3) check (top_confidence between 0 and 1),
  outcome detection_outcome not null,
  alerted boolean not null generated always as (outcome = 'ALERTED') stored,
  created_at timestamptz not null default now()
);
create index detections_device_created_idx on detections (device_id, created_at desc);

-- ---------------------------------------------------------------------
-- alerts
-- ---------------------------------------------------------------------
create table alerts (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null references devices(id) on delete cascade,
  detection_id uuid not null unique references detections(id) on delete cascade,
  category text not null,
  priority priority_level not null,
  context text not null references contexts(code),
  confidence numeric(4,3) not null check (confidence between 0 and 1),
  icon text not null,
  message text not null,
  vibration_count smallint not null check (vibration_count between 0 and 10),
  status alert_status not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  acknowledged_at timestamptz,
  check ((status = 'ACKNOWLEDGED') = (acknowledged_at is not null))
);
create index alerts_device_created_idx on alerts (device_id, created_at desc);
create index alerts_device_category_idx on alerts (device_id, category, created_at desc);

-- ---------------------------------------------------------------------
-- Seguridad
-- ---------------------------------------------------------------------
alter table users enable row level security;
alter table contexts enable row level security;
alter table devices enable row level security;
alter table device_settings enable row level security;
alter table sound_rules enable row level security;
alter table detections enable row level security;
alter table alerts enable row level security;
