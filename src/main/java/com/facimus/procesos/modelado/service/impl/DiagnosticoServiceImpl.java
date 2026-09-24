package com.facimus.procesos.modelado.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.modelado.dto.response.DiagnosticoResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.HallazgoDiagnosticoResponse;
import com.facimus.procesos.modelado.model.CodigoDeDiagnostico;
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
    public DiagnosticoResponse diagnosticar(Long empresaId, Long procesoId, String sinElemento) {
        if (!StringUtils.hasText(sinElemento)) {
            return revisar(diagramaService.obtener(empresaId, procesoId));
        }
        // Se lee antes de consultar: si no se entiende, no vale la pena armar el diagrama.
        ElementoDelDiagrama elemento = ElementoDelDiagrama.de(sinElemento);
        return simular(diagramaService.obtener(empresaId, procesoId), elemento);
    }

    /** Puro sobre el diagrama: ni consulta, ni reloj, ni azar, asi que dos diagnosticos iguales se comparan. */
    static DiagnosticoResponse revisar(DiagramaResponse diagrama) {
        Hallazgos hallazgos = new Hallazgos();
        avisarDelBorrador(diagrama, hallazgos);
        revisar(new MapaDelDiagrama(diagrama), hallazgos);
        return armar(diagrama, null, hallazgos);
    }

    /** El diagrama que quedaria sin el elemento, revisado igual que el de verdad, y lo que el borrado se lleva. */
    private static DiagnosticoResponse simular(DiagramaResponse diagrama, ElementoDelDiagrama elemento) {
        MapaDelDiagrama antes = new MapaDelDiagrama(diagrama);
        MapaDelDiagrama despues = new MapaDelDiagrama(SimulacionDeBorrado.sinElElemento(diagrama, elemento));

        Hallazgos hallazgos = new Hallazgos();
        avisarDelBorrador(diagrama, hallazgos);
        SimulacionDeBorrado.avisarDeLoQueSeVa(antes, despues, elemento, hallazgos);
        revisar(despues, hallazgos);
        return armar(diagrama, elemento.texto(), hallazgos);
    }

    /**
     * A-13: lo que se esta mirando es el borrador de trabajo, y lo que la invitada lee y la ejecucion usara es la
     * version publicada. Llega vacio en un listado y para una invitada, que no ven el borrador de nadie.
     */
    private static void avisarDelBorrador(DiagramaResponse diagrama, Hallazgos hallazgos) {
        ProcesoResponse proceso = diagrama.proceso();
        if (Boolean.TRUE.equals(proceso.borradorPendiente())) {
            hallazgos.anotar(CodigoDeDiagnostico.A13,
                    "Hay cambios en el diagrama que la version " + proceso.versionPublicada() + " no incluye.",
                    "Publica el proceso otra vez para que la version vigente sea este diagrama.");
        }
    }

    private static void revisar(MapaDelDiagrama mapa, Hallazgos hallazgos) {
        RevisionDelFlujo.revisar(mapa, hallazgos);
        RevisionDeLosMensajes.revisar(mapa, hallazgos);
    }

    private static DiagnosticoResponse armar(DiagramaResponse diagrama, String sinElemento, Hallazgos hallazgos) {
        List<HallazgoDiagnosticoResponse> encontrados = hallazgos.ordenados();
        long errores = encontrados.stream().filter(hallazgo -> hallazgo.severidad() == Severidad.ALTA).count();
        return new DiagnosticoResponse(diagrama.proceso().id(), sinElemento, (int) errores,
                encontrados.size() - (int) errores, encontrados);
    }
}
