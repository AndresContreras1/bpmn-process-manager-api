-- PR 71: los plazos de las tareas y de los casos, su estado, los avisos y el escalamiento con el evento de borde.

-- El plazo de un nodo: el de la tarea, en una actividad de usuario, o el del caso entero, en un evento de inicio. Se
-- cuenta en horas de reloj o en dias habiles del calendario de la tienda, con los mismos topes que un temporizador: un
-- ano de horas o 366 dias habiles.
alter table nodos_flujo add column plazo_cantidad integer;
alter table nodos_flujo add column plazo_unidad varchar(20);
alter table nodos_flujo add constraint ck_nodos_flujo_plazo
    check (plazo_cantidad is null and plazo_unidad is null
        or plazo_unidad = 'HORAS' and plazo_cantidad between 1 and 8784
        or plazo_unidad = 'DIAS_HABILES' and plazo_cantidad between 1 and 366);
alter table nodos_flujo add constraint ck_nodos_flujo_plazo_de_tarea_o_inicio
    check (plazo_unidad is null
        or tipo_nodo = 'ACTIVIDAD' and tipo_actividad = 'USUARIO'
        or tipo_nodo = 'EVENTO' and tipo_evento in ('INICIO', 'MENSAJE_INICIO', 'TEMPORIZADOR_INICIO'));

-- Lo que hace un temporizador de borde que no interrumpe, ademas de abrir su camino: avisar a la tarea y a quien la
-- supervisa, o pasarle la tarea. El supervisor es escalar_a, o el dueno del proceso si no dice nadie; sin clave foranea,
-- como la persona fija de una asignacion: un BPMN importado puede traer un id que aqui no es nadie.
alter table nodos_flujo add column escalamiento varchar(20);
alter table nodos_flujo add column escalar_a bigint;
alter table nodos_flujo add constraint ck_nodos_flujo_escalamiento
    check (escalamiento is null and escalar_a is null or escalamiento in ('AVISAR', 'REASIGNAR'));
alter table nodos_flujo add constraint ck_nodos_flujo_escalamiento_de_borde
    check (escalamiento is null or tipo_nodo = 'EVENTO' and tipo_evento = 'TEMPORIZADOR_BORDE' and interrumpe = false);

-- Cuando vence una tarea y cuando entra en su ultimo cuarto (por vencer): en produccion una hora UTC, en prueba un tick
-- del reloj de la tienda. El estado lo mueve el repaso de los plazos; al completarla se queda en lo que llego a ser, y
-- VENCIDA dice que se hizo tarde.
alter table actividades_caso add column vence_en timestamp(6);
alter table actividades_caso add column aviso_en timestamp(6);
alter table actividades_caso add column vence_tick integer;
alter table actividades_caso add column aviso_tick integer;
alter table actividades_caso add column estado_plazo varchar(20);
alter table actividades_caso add constraint ck_actividades_caso_plazo
    check (estado_plazo is null and vence_en is null and aviso_en is null and vence_tick is null
               and aviso_tick is null
        or estado_plazo in ('A_TIEMPO', 'POR_VENCER', 'VENCIDA')
               and (vence_en is not null and aviso_en is not null and aviso_en <= vence_en
                        and vence_tick is null and aviso_tick is null
                    or vence_tick is not null and aviso_tick is not null and aviso_tick <= vence_tick
                        and vence_en is null and aviso_en is null));
-- El repaso busca lo que espera y cambia de estado; la bandeja ordena por vencimiento.
create index idx_actividades_caso_aviso on actividades_caso (aviso_en)
    where estado = 'EN_ESPERA' and estado_plazo = 'A_TIEMPO';
create index idx_actividades_caso_vence on actividades_caso (vence_en)
    where estado = 'EN_ESPERA' and estado_plazo = 'POR_VENCER';
create index idx_actividades_caso_ticks on actividades_caso (empresa_id, aviso_tick, vence_tick)
    where estado = 'EN_ESPERA' and estado_plazo in ('A_TIEMPO', 'POR_VENCER');

-- Lo mismo para el caso entero, desde el evento de inicio que lo abrio.
alter table casos add column vence_en timestamp(6);
alter table casos add column aviso_en timestamp(6);
alter table casos add column vence_tick integer;
alter table casos add column aviso_tick integer;
alter table casos add column estado_plazo varchar(20);
alter table casos add constraint ck_casos_plazo
    check (estado_plazo is null and vence_en is null and aviso_en is null and vence_tick is null
               and aviso_tick is null
        or estado_plazo in ('A_TIEMPO', 'POR_VENCER', 'VENCIDA')
               and (vence_en is not null and aviso_en is not null and aviso_en <= vence_en
                        and vence_tick is null and aviso_tick is null
                    or vence_tick is not null and aviso_tick is not null and aviso_tick <= vence_tick
                        and vence_en is null and aviso_en is null));
create index idx_casos_aviso on casos (aviso_en)
    where estado in ('ABIERTO', 'ERROR') and estado_plazo = 'A_TIEMPO';
create index idx_casos_vence on casos (vence_en)
    where estado in ('ABIERTO', 'ERROR') and estado_plazo = 'POR_VENCER';
create index idx_casos_ticks on casos (empresa_id, aviso_tick, vence_tick)
    where estado in ('ABIERTO', 'ERROR') and estado_plazo in ('A_TIEMPO', 'POR_VENCER');

-- La linea de tiempo cuenta cada cambio de estado de un plazo y cada escalamiento.
alter table eventos_caso drop constraint ck_eventos_caso_tipo;
alter table eventos_caso add constraint ck_eventos_caso_tipo check (tipo in ('CASO_ABIERTO', 'NODO_ACTIVADO',
    'TAREA_CREADA', 'TAREA_COMPLETADA', 'GATEWAY_DECIDIO', 'MENSAJE_ENVIADO', 'MENSAJE_RECIBIDO', 'ENVIO_FALLIDO',
    'SIN_CAMINO', 'VARIABLE_AUSENTE', 'CASO_TERMINADO', 'CASO_CANCELADO', 'TEMPORIZADOR_PROGRAMADO',
    'TEMPORIZADOR_DISPARADO', 'TAREA_ASIGNADA', 'TAREA_RECLAMADA', 'TAREA_LIBERADA', 'TAREA_REASIGNADA',
    'TAREA_DELEGADA', 'PLAZO_POR_VENCER', 'PLAZO_VENCIDO', 'TAREA_ESCALADA'));

-- Los avisos llegan por el centro de notificaciones del PR 63, con sus tres tipos nuevos.
alter table notificaciones drop constraint ck_notificaciones_tipo;
alter table notificaciones add constraint ck_notificaciones_tipo
    check (tipo in ('MENCION', 'RESPUESTA', 'HILO_RESUELTO', 'PLAZO_POR_VENCER', 'PLAZO_VENCIDO', 'TAREA_ESCALADA'));
alter table preferencias_notificacion drop constraint ck_preferencias_notificacion_tipo;
alter table preferencias_notificacion add constraint ck_preferencias_notificacion_tipo
    check (tipo in ('MENCION', 'RESPUESTA', 'HILO_RESUELTO', 'PLAZO_POR_VENCER', 'PLAZO_VENCIDO', 'TAREA_ESCALADA'));
