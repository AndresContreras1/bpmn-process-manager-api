/**
 * Los casos que corren una version publicada: pasos, bandejas, mensajes, el reloj y el motor (D1).
 * Lee lo publicado de gestion y de modelado, y publica el puerto por el que llega a los socios. PR 68: escucha la
 * publicacion y el retiro de versiones de gestion, que deciden que inicios temporizados quedan armados.
 */
@ApplicationModule(displayName = "Ejecucion", allowedDependencies = {"common", "gestion::service",
        "gestion::dto-response", "gestion::model", "gestion::event", "modelado::dto-response", "modelado::model"})
package com.facimus.procesos.ejecucion;

import org.springframework.modulith.ApplicationModule;
