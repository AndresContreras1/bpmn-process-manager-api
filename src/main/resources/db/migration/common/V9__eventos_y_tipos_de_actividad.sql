-- Eventos de flujo: el tercer subtipo de nodos_flujo, junto a las actividades y los gateways. Comparte la tabla
-- (SINGLE_TABLE), asi que un arco sigue apuntando a cualquier nodo sin saber de que tipo es.
alter table nodos_flujo add column tipo_evento varchar(20);

-- Tipo de actividad: quien hace el trabajo, y si ese trabajo es intercambiar un mensaje. Las actividades que ya
-- existen son trabajo de una persona del rol de su lane.
alter table nodos_flujo add column tipo_actividad varchar(20);
update nodos_flujo set tipo_actividad = 'USUARIO' where tipo_nodo = 'ACTIVIDAD';

-- Cada subtipo llena solo su columna, y la llena siempre: los checks valen para las filas de su tipo y dejan pasar
-- las de los otros, porque una comparacion con null no es falsa, es desconocida.
alter table nodos_flujo drop constraint ck_nodos_flujo_tipo_nodo;
alter table nodos_flujo add constraint ck_nodos_flujo_tipo_nodo
    check (tipo_nodo in ('ACTIVIDAD', 'GATEWAY', 'EVENTO'));
alter table nodos_flujo add constraint ck_nodos_flujo_tipo_evento
    check (tipo_evento in ('INICIO', 'FIN', 'MENSAJE_INICIO', 'MENSAJE_INTERMEDIO', 'MENSAJE_FIN'));
alter table nodos_flujo add constraint ck_nodos_flujo_evento_con_tipo
    check (tipo_nodo <> 'EVENTO' or tipo_evento is not null);
alter table nodos_flujo add constraint ck_nodos_flujo_tipo_actividad
    check (tipo_actividad in ('USUARIO', 'SERVICIO', 'ENVIO', 'RECEPCION'));
alter table nodos_flujo add constraint ck_nodos_flujo_actividad_con_tipo
    check (tipo_nodo <> 'ACTIVIDAD' or tipo_actividad is not null);
