/**
 * Lo que se dibuja en un proceso: participantes, lanes, nodos, flujos, mensajes, el diagnostico y la
 * revision con IA. Se apoya en gestion para el proceso y sus roles.
 */
@ApplicationModule(displayName = "Modelado", allowedDependencies = {"common", "gestion::service",
        "gestion::dto-response", "gestion::model", "gestion::repository", "gestion::event"})
package com.facimus.procesos.modelado;

import org.springframework.modulith.ApplicationModule;
