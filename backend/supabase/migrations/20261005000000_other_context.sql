-- =====================================================================
-- SoundAlert: contexto OTHER (OTRO en el reloj)
--
-- Contextos activos: HOME (CASA), STREET (CALLE), OTHER (OTRO).
-- UNIVERSITY y WORK se conservan como históricos (datos ya guardados y sus
-- reglas), pero la API ya no permite fijarlos como contexto activo.
--
-- Solo añade datos al catálogo de contextos: no cambia tablas ni columnas.
-- devices.current_context, detections.context y alerts.context referencian
-- contexts(code), así que OTHER debe existir antes de usarse.
-- =====================================================================

insert into contexts (code, name, icon, sort_order)
values ('OTHER', 'Otro', 'other', 5)
on conflict (code) do nothing;
