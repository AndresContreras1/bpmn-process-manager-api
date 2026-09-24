-- Un mensaje deja de ser una flecha entre dos cajas: se ancla al nodo que lo manda y al que lo espera, dice por
-- donde sale, que datos lleva y que hace el proceso si el envio falla. Todas las columnas son opcionales o traen
-- valor por defecto, asi que los mensajes que ya existen siguen siendo validos.
alter table mensajes add column nodo_origen_id bigint;
alter table mensajes add column nodo_destino_id bigint;
alter table mensajes add column nodo_manejo_error_id bigint;
alter table mensajes add column tipo_destino varchar(20);
alter table mensajes add column si_falla varchar(20) default 'CONTINUAR' not null;
alter table mensajes add column origen_externo boolean default false not null;
alter table mensajes add column campos varchar(4000);
alter table mensajes add column uso_de_los_datos varchar(1000);
alter table mensajes add column variable varchar(60);
alter table mensajes add column respuesta_id bigint;

alter table mensajes add constraint ck_mensajes_tipo_destino
    check (tipo_destino in ('CORREO', 'SERVICIO_WEB', 'COLA'));
alter table mensajes add constraint ck_mensajes_si_falla
    check (si_falla in ('CONTINUAR', 'MANEJAR_ERROR', 'FINALIZAR'));
-- Desviar el flujo exige decir a donde: la regla vive en el service y la base la sostiene.
alter table mensajes add constraint ck_mensajes_manejo_de_error
    check (si_falla <> 'MANEJAR_ERROR' or nodo_manejo_error_id is not null);
alter table mensajes add constraint ck_mensajes_respuesta_distinta
    check (respuesta_id is null or respuesta_id <> id);
alter table mensajes add constraint fk_mensajes_nodo_origen
    foreign key (nodo_origen_id) references nodos_flujo (id);
alter table mensajes add constraint fk_mensajes_nodo_destino
    foreign key (nodo_destino_id) references nodos_flujo (id);
alter table mensajes add constraint fk_mensajes_nodo_manejo_error
    foreign key (nodo_manejo_error_id) references nodos_flujo (id);
alter table mensajes add constraint fk_mensajes_respuesta
    foreign key (respuesta_id) references mensajes (id);

-- La clave de correlacion deja de ser solo una etiqueta: dice en que campo del cuerpo viaja y que hacer con un
-- mensaje que no corresponde a ningun caso abierto.
alter table correlaciones add column campo varchar(80);
alter table correlaciones add column sin_caso varchar(20) default 'DESCARTAR' not null;
alter table correlaciones add constraint ck_correlaciones_sin_caso
    check (sin_caso in ('DESCARTAR', 'INICIAR_CASO'));

-- Con que clase de socio habla un participante de caja negra.
alter table pools add column integracion varchar(20) default 'NINGUNA' not null;
alter table pools add constraint ck_pools_integracion
    check (integracion in ('NINGUNA', 'CLIENTE', 'PAGOS', 'TRANSPORTE', 'NOTIFICACIONES'));
