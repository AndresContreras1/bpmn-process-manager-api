-- PR 36: los indices que faltaban, segun los EXPLAIN de las consultas de toda la suite.
--
-- Cada clave foranea tiene ahora un indice que empieza por sus columnas. Sin el, borrar una fila de la tabla a la
-- que apunta obliga a PostgreSQL a leer entera la tabla que la referencia, una vez por fila borrada: el borrado de una
-- tienda (PR 35) borra sus casos, sus nodos y sus personas, y cada uno revisaba asi las tablas de todas las tiendas.
-- Los indices de las tablas de la ejecucion empiezan por empresa_id, que sirve a las consultas pero no a esa
-- revision, que busca solo por la columna de la clave. SaludDeLaBaseIntegracionTest falla si aparece una clave
-- foranea sin el suyo.
--
-- La purga de la noche borra los refresh tokens vencidos y las claves de idempotencia viejas por su fecha, que
-- tampoco tenia indice.
--
-- Son CREATE INDEX normales, dentro de la transaccion de la migracion: bloquean la escritura de cada tabla mientras se
-- construye su indice. Con tablas grandes en produccion se crearian antes, a mano, con CONCURRENTLY.

-- Quien creo y quien cambio cada elemento: se revisan al borrar una persona.
create index idx_usuarios_creado_por on usuarios (creado_por);
create index idx_usuarios_modificado_por on usuarios (modificado_por);
create index idx_procesos_creado_por on procesos (creado_por);
create index idx_procesos_modificado_por on procesos (modificado_por);
create index idx_roles_proceso_creado_por on roles_proceso (creado_por);
create index idx_roles_proceso_modificado_por on roles_proceso (modificado_por);
create index idx_pools_creado_por on pools (creado_por);
create index idx_pools_modificado_por on pools (modificado_por);
create index idx_lanes_creado_por on lanes (creado_por);
create index idx_lanes_modificado_por on lanes (modificado_por);
create index idx_nodos_flujo_creado_por on nodos_flujo (creado_por);
create index idx_nodos_flujo_modificado_por on nodos_flujo (modificado_por);
create index idx_arcos_creado_por on arcos (creado_por);
create index idx_arcos_modificado_por on arcos (modificado_por);
create index idx_mensajes_creado_por on mensajes (creado_por);
create index idx_mensajes_modificado_por on mensajes (modificado_por);
create index idx_correlaciones_creado_por on correlaciones (creado_por);
create index idx_correlaciones_modificado_por on correlaciones (modificado_por);
create index idx_historial_cambios_autor on historial_cambios (autor_id);
create index idx_versiones_proceso_publicado_por on versiones_proceso (publicado_por);
create index idx_actividades_caso_asignado_a on actividades_caso (asignado_a);
create index idx_eventos_caso_autor on eventos_caso (autor_id);
create index idx_enlaces_usuario on enlaces_de_un_uso (usuario_id);
create index idx_vinculos_pendientes_usuario on vinculos_pendientes (usuario_id);

-- El modelo: un arco sale de un nodo, y un mensaje sale de uno, llega a otro, puede tener el suyo para los errores
-- y contestar a otro mensaje.
create index idx_arcos_origen on arcos (origen_id);
create index idx_mensajes_nodo_origen on mensajes (nodo_origen_id);
create index idx_mensajes_nodo_destino on mensajes (nodo_destino_id);
create index idx_mensajes_nodo_manejo_error on mensajes (nodo_manejo_error_id);
create index idx_mensajes_respuesta on mensajes (respuesta_id);
create index idx_membresias_rol_rol_proceso on membresias_rol (rol_proceso_id);

-- La ejecucion: un caso es de un proceso y de una version, y de el cuelgan sus tareas, sus eventos y sus mensajes.
create index idx_casos_proceso on casos (proceso_id);
create index idx_casos_version on casos (version_proceso_id);
create index idx_actividades_caso_caso on actividades_caso (caso_id);
create index idx_eventos_caso_caso_id on eventos_caso (caso_id);
create index idx_mensajes_salientes_caso_id on mensajes_salientes (caso_id);
create index idx_mensajes_entrantes_caso on mensajes_entrantes (caso_id);
create index idx_mensajes_entrantes_proceso_id on mensajes_entrantes (proceso_id);

-- La tienda de un trabajo de la cola y de un enlace del correo.
create index idx_trabajos_empresa on trabajos (empresa_id);
create index idx_enlaces_empresa on enlaces_de_un_uso (empresa_id);

-- La purga de la noche.
create index idx_refresh_tokens_expiracion on refresh_tokens (fecha_expiracion);
create index idx_claves_idempotencia_fecha on claves_idempotencia (fecha_creacion);
