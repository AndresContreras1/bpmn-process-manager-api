package com.facimus.procesos.modelado.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.modelado.dto.response.DiagnosticoResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.HallazgoDiagnosticoResponse;
import com.facimus.procesos.modelado.model.Severidad;
import com.facimus.procesos.modelado.service.DiagnosticoService;
import com.facimus.procesos.modelado.service.DiagramaService;

import lombok.RequiredArgsConstructor;

/**
 * La revision se hace sobre el diagrama ya armado, no sobre la base: el mismo codigo sirve para el borrador que se
 * esta editando y para la instantanea de una version publicada. El diagrama entra por la puerta de lectura, asi que
 * la invitada de HU-23 tambien puede pedir el diagnostico de lo que le compartieron.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiagnosticoServiceImpl implements DiagnosticoService {

    private final DiagramaService diagramaService;

    @Override
    public DiagnosticoResponse diagnosticar(Long empresaId, Long procesoId) {
        return revisar(diagramaService.obtener(empresaId, procesoId));
    }

    /** Puro sobre el diagrama: ni consulta, ni reloj, ni azar, asi que dos diagnosticos iguales se comparan. */
    static DiagnosticoResponse revisar(DiagramaResponse diagrama) {
        Hallazgos hallazgos = new Hallazgos();
        MapaDelDiagrama mapa = new MapaDelDiagrama(diagrama);
        RevisionDelFlujo.revisar(mapa, hallazgos);
        RevisionDeLosMensajes.revisar(mapa, hallazgos);

        List<HallazgoDiagnosticoResponse> encontrados = hallazgos.ordenados();
        long errores = encontrados.stream().filter(hallazgo -> hallazgo.severidad() == Severidad.ALTA).count();
        return new DiagnosticoResponse(diagrama.proceso().id(), (int) errores,
                encontrados.size() - (int) errores, encontrados);
    }
}
