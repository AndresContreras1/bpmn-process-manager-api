-- D8: el tiempo de la simulacion lo mueve quien prueba, no el reloj de pared. Cada tienda tiene el suyo, un
-- contador de ticks que empieza en cero y solo sube, y decide si lo mueve a mano o si un trabajo lo mueve solo.
--
-- Vive en la configuracion de la tienda porque es una decision suya, como la politica de estructura: dos tiendas de
-- la misma instalacion pueden estar en momentos distintos de su propia simulacion sin verse.
alter table configuracion_tienda add column reloj integer default 0 not null;
alter table configuracion_tienda add column modo_simulacion varchar(20) default 'MANUAL' not null;
alter table configuracion_tienda add constraint ck_configuracion_tienda_modo
    check (modo_simulacion in ('MANUAL', 'AUTOMATICO'));
alter table configuracion_tienda add constraint ck_configuracion_tienda_reloj check (reloj >= 0);
