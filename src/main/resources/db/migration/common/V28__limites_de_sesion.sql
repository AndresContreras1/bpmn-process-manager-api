-- PR 35 (NIST SP 800-63B-4, AAL2): cada tienda decide cuanto puede pasar una sesion sin renovarse y cuanto puede
-- durar en total, dentro de lo que la norma permite. Las tiendas de hoy quedan en el tope: una hora y un dia.
alter table configuracion_tienda add column inactividad_sesion_minutos integer default 60 not null;
alter table configuracion_tienda add column duracion_sesion_horas integer default 24 not null;
alter table configuracion_tienda add constraint ck_configuracion_inactividad_sesion
    check (inactividad_sesion_minutos between 30 and 60);
alter table configuracion_tienda add constraint ck_configuracion_duracion_sesion
    check (duracion_sesion_horas between 1 and 24);
