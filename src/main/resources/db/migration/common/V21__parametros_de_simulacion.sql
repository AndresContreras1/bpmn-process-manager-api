-- D7: como se portan los socios de una tienda. Nada de esto es real: son simuladores, y estos numeros son lo que
-- cada tienda decide que le pase. Con la misma semilla y los mismos pasos, la simulacion da siempre lo mismo, que
-- es lo que separa una demo que se puede repetir de una que a veces sale de otra manera.
alter table configuracion_tienda add column semilla bigint default 42 not null;
alter table configuracion_tienda add column tasa_rechazo_pagos integer default 10 not null;
alter table configuracion_tienda add column ticks_respuesta_pagos integer default 1 not null;
alter table configuracion_tienda add column regla_rechazo_pagos varchar(500);
alter table configuracion_tienda add column ticks_respuesta_transporte integer default 1 not null;
alter table configuracion_tienda add column ticks_entrega integer default 3 not null;
alter table configuracion_tienda add column tasa_perdida_envios integer default 5 not null;
alter table configuracion_tienda add column tasa_fallo_notificaciones integer default 2 not null;
-- Las tasas son porcentajes y las latencias, ticks: cero seria un socio que contesta antes de recibir.
alter table configuracion_tienda add constraint ck_configuracion_tienda_tasas check (
    tasa_rechazo_pagos between 0 and 100
    and tasa_perdida_envios between 0 and 100
    and tasa_fallo_notificaciones between 0 and 100);
alter table configuracion_tienda add constraint ck_configuracion_tienda_ticks check (
    ticks_respuesta_pagos >= 1 and ticks_respuesta_transporte >= 1 and ticks_entrega >= 1);

-- Un socio puede contestar mas tarde: el transportista confirma la entrega varios ticks despues de recoger el
-- paquete. Esa respuesta se guarda ya, con el tick en que le toca, y el reloj la recoge cuando llega.
alter table mensajes_entrantes add column tick_disponible integer default 0 not null;
alter table mensajes_entrantes drop constraint ck_mensajes_entrantes_resultado;
alter table mensajes_entrantes add constraint ck_mensajes_entrantes_resultado
    check (resultado in ('ENTREGADO_A_CASO', 'CASO_NUEVO', 'EN_ESPERA', 'PROGRAMADO', 'DESCARTADO'));
-- Lo que el reloj recoge en cada tick: lo que espera a alguien y lo que espera a su momento.
create index idx_mensajes_entrantes_pendientes on mensajes_entrantes (empresa_id, resultado, tick_disponible);
