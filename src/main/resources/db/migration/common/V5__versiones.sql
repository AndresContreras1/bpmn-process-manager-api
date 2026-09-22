-- Bloqueo optimista: cada recurso que los usuarios editan lleva una version que sube con cada cambio guardado. Quien
-- edita manda la version que leyo; si otra persona guardo antes, la API responde 409 en vez de pisar ese cambio.
alter table procesos add column version bigint default 0 not null;
alter table roles_proceso add column version bigint default 0 not null;
alter table usuarios add column version bigint default 0 not null;
alter table pools add column version bigint default 0 not null;
alter table lanes add column version bigint default 0 not null;
alter table nodos_flujo add column version bigint default 0 not null;
alter table arcos add column version bigint default 0 not null;
alter table mensajes add column version bigint default 0 not null;
alter table correlaciones add column version bigint default 0 not null;
