-- SOLO DESARROLLO: borra todas las tablas/tipos de SoundAlert (y sus datos)
-- para volver a ejecutar migrations/20260930000000_init.sql desde cero.
drop table if exists alerts, detections, sound_rules, rule_overrides, device_settings, devices, contexts, users cascade;
drop function if exists create_default_device_settings() cascade;
drop function if exists set_updated_at() cascade;
drop type if exists alert_status, detection_outcome, classification_source, device_platform, priority_level, user_context cascade;
