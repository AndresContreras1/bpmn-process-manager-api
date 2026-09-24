package com.facimus.procesos.modelado.service.impl;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.service.Instantanea;
import com.facimus.procesos.gestion.service.InstantaneaDelModelo;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lo que se guarda es el mismo JSON que devuelve GET /procesos/{id}/diagrama, para que quien lea una version
 * publicada no tenga que entender otro formato. Lo que se compara es su forma canonica, que deja fuera la auditoria.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstantaneaDelDiagrama implements InstantaneaDelModelo {

    private final ArmadoDelDiagrama armado;
    private final JsonMapper json;

    @Override
    public Instantanea tomar(Long empresaId, ProcesoResponse proceso) {
        DiagramaResponse diagrama = armado.armar(proceso, false, empresaId);
        return new Instantanea(json.writeValueAsString(diagrama), Huella.de(DiagramaCanonico.de(diagrama, json)));
    }
}
