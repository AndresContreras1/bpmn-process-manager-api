-- PR 49: a quien le exige cada tienda un segundo factor: a nadie, a los administradores o a todos. Quien lo necesita
-- y no lo tiene entra, pero solo puede inscribirlo. Las tiendas de hoy quedan como estaban: opcional.
alter table configuracion_tienda add column politica_segundo_factor varchar(20) default 'OPCIONAL' not null;
alter table configuracion_tienda add constraint ck_configuracion_politica_segundo_factor
    check (politica_segundo_factor in ('OPCIONAL', 'ADMINISTRADORES', 'TODOS'));
