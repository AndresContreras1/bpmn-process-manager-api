/**
 * La cadena de filtros, los tokens y el login. Usa los servicios de gestion para las cuentas y las
 * sesiones, y escucha sus eventos.
 */
@ApplicationModule(displayName = "Security", allowedDependencies = {"common", "gestion::service",
        "gestion::dto-request", "gestion::dto-response", "gestion::event"})
package com.facimus.procesos.security;

import org.springframework.modulith.ApplicationModule;
