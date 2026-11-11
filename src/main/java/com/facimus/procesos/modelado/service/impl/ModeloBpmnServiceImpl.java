package com.facimus.procesos.modelado.service.impl;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.dto.response.ProcesoLectura;
import com.facimus.procesos.gestion.service.ModeloBpmn;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.bpmn.EscritorBpmn;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.repository.ExtensionXmlRepository;
import com.facimus.procesos.modelado.service.GeometriaService;
import com.facimus.procesos.modelado.service.ModeloBpmnService;

import lombok.RequiredArgsConstructor;

/**
 * El proceso entra por la puerta de lectura, como el diagrama: uno ajeno o eliminado responde 404 antes de escribir
 * nada, y uno compartido se responde con lo que la otra tienda publico.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModeloBpmnServiceImpl implements ModeloBpmnService {

    private final ProcesoService procesoService;
    private final VersionService versionService;
    private final ArmadoDelDiagrama armado;
    private final GeometriaService geometriaService;
    private final ExtensionXmlRepository extensionXmlRepository;

    @Override
    public ModeloBpmn exportar(Long empresaId, Long procesoId) {
        ProcesoLectura lectura = procesoService.obtenerParaLectura(empresaId, procesoId);
        if (lectura.compartido()) {
            return versionService.modeloVigente(lectura.empresaPropietariaId(), procesoId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
        }
        DiagramaResponse diagrama = armado.armar(lectura.proceso(), false, lectura.empresaPropietariaId());
        byte[] bpmn = EscritorBpmn.escribir(diagrama,
                geometriaService.obtener(lectura.empresaPropietariaId(), procesoId),
                ExtensionesGuardadas.de(extensionXmlRepository, procesoId, lectura.empresaPropietariaId()));
        return ModeloBpmn.delBorrador(lectura.proceso().nombre(), new String(bpmn, StandardCharsets.UTF_8));
    }
}
