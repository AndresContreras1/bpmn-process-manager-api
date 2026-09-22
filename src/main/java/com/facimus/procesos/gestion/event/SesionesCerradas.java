package com.facimus.procesos.gestion.event;

import java.util.List;

/**
 * Se publica al cerrar sesiones. La capa de seguridad la escucha para rechazar desde ya los access tokens de esas
 * sesiones, que siguen firmados hasta vencer: asi el filtro JWT no consulta la base en cada peticion.
 */
public record SesionesCerradas(List<String> codigos) {
}
