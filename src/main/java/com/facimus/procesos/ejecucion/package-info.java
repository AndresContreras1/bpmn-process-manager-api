/**
 * Los casos que corren una version publicada: pasos, bandejas, mensajes, el reloj y el motor (D1).
 * Lee lo publicado de gestion y de modelado, y publica el puerto por el que llega a los socios.
 */
@ApplicationModule(displayName = "Ejecucion", allowedDependencies = {"common", "gestion::service",
        "gestion::dto-response", "gestion::model", "modelado::dto-response", "modelado::model"})
package com.facimus.procesos.ejecucion;

import org.springframework.modulith.ApplicationModule;
