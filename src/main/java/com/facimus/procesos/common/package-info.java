/**
 * Lo que todos los modulos necesitan: la tienda, el rol de acceso, la identidad autenticada, la entidad base
 * con su tienda, los errores, la paginacion, las condiciones y las metricas. Es abierto: cualquier modulo
 * usa cualquier cosa de aqui, y de aqui no se usa ningun otro.
 */
@ApplicationModule(displayName = "Common", type = ApplicationModule.Type.OPEN)
package com.facimus.procesos.common;

import org.springframework.modulith.ApplicationModule;
