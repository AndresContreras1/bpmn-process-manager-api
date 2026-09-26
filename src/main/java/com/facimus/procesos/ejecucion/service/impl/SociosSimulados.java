package com.facimus.procesos.ejecucion.service.impl;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.facimus.procesos.ejecucion.puerto.SocioSimulado;
import com.facimus.procesos.modelado.model.Integracion;

/**
 * Que socio atiende a cada clase de participante. Se arma una vez con los que haya registrados; el que dice
 * NINGUNA es el que atiende a los demas mientras no exista uno suyo, que es como el eco cubre a todos antes de que
 * lleguen la pasarela, el transportista y el resto.
 */
@Component
class SociosSimulados {

    private final Map<Integracion, SocioSimulado> porIntegracion;
    private final SocioSimulado porDefecto;

    SociosSimulados(List<SocioSimulado> socios) {
        this.porIntegracion = socios.stream()
                .collect(Collectors.toMap(SocioSimulado::integracion, Function.identity()));
        this.porDefecto = porIntegracion.get(Integracion.NINGUNA);
    }

    SocioSimulado paraA(Integracion integracion) {
        return porIntegracion.getOrDefault(integracion, porDefecto);
    }
}
