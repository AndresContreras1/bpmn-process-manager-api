package com.facimus.procesos.modelado.service.impl;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.service.Instantanea;
import com.facimus.procesos.gestion.service.InstantaneaDelModelo;
import com.facimus.procesos.modelado.bpmn.EscritorBpmn;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.repository.ExtensionXmlRepository;
import com.facimus.procesos.modelado.service.GeometriaService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lo que se guarda es el mismo JSON que devuelve GET /procesos/{id}/diagrama, para que quien lea una version
 * publicada no tenga que entender otro formato. Lo que se compara es su forma canonica, que deja fuera la auditoria.
 * Desde el PR 53 va tambien en BPMN, escrito con la geometria que el borrador tiene en ese momento.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstantaneaDelDiagrama implements InstantaneaDelModelo {

    private final ArmadoDelDiagrama armado;
    private final GeometriaService geometriaService;
    private final ExtensionXmlRepository extensionXmlRepository;
    private final JsonMapper json;

    @Override
    public Instantanea tomar(Long empresaId, ProcesoResponse proceso) {
        DiagramaResponse diagrama = armado.armar(proceso, false, empresaId);
        byte[] bpmn = EscritorBpmn.escribir(diagrama, geometriaService.obtener(empresaId, proceso.id()),
                ExtensionesGuardadas.de(extensionXmlRepository, proceso.id(), empresaId));
        return new Instantanea(json.writeValueAsString(diagrama), Huella.de(DiagramaCanonico.de(diagrama, json)),
                new String(bpmn, StandardCharsets.UTF_8));
    }
}
