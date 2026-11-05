/**
 * Los socios simulados detras del puerto de ejecucion (D7): solo conocen ese puerto, el tipo de socio
 * que declara un participante y el lenguaje de las condiciones.
 */
@ApplicationModule(displayName = "Integracion", allowedDependencies = {"common", "ejecucion::puerto",
        "modelado::model"})
package com.facimus.procesos.integracion;

import org.springframework.modulith.ApplicationModule;
