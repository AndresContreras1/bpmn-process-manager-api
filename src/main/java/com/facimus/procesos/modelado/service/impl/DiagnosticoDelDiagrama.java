package com.facimus.procesos.modelado.service.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.service.DiagnosticoDelModelo;
import com.facimus.procesos.modelado.dto.response.HallazgoDiagnosticoResponse;
import com.facimus.procesos.modelado.model.CodigoDeDiagnostico;
import com.facimus.procesos.modelado.model.Severidad;
import com.facimus.procesos.modelado.repository.ActividadRepository;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;

/**
 * R-44: lo que impide publicar son los errores del diagnostico, los mismos que ve quien edita el diagrama. Las
 * advertencias no impiden nada: un proceso con una decision sin salida por defecto se publica, y el aviso sigue ahi.
 *
 * <p>Arma el diagrama por su cuenta en vez de pedirselo a DiagnosticoService, que lo pediria a la puerta de lectura
 * de gestion: gestion ya comprobo que el proceso es suyo, y volver a entrar por ahi cerraria un circulo.</p>
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiagnosticoDelDiagrama implements DiagnosticoDelModelo {

    private final ArmadoDelDiagrama armado;
    private final ObservationRegistry observaciones;
    private final ActividadRepository actividadRepository;

    /** El mismo span que el diagnostico que pide quien edita, marcado como el que decide si se publica. */
    @Override
    public List<String> errores(Long empresaId, ProcesoResponse proceso) {
        return Observation.createNotStarted("procesos.diagnostico", observaciones)
                .contextualName("diagnosticar antes de publicar")
                .lowCardinalityKeyValue("uso", "publicacion")
                .highCardinalityKeyValue("proceso.id", String.valueOf(proceso.id()))
                .observe(() -> {
                    List<String> errores = new ArrayList<>(DiagnosticoServiceImpl.revisar(
                            armado.armar(proceso, false, empresaId)).hallazgos().stream()
                            .filter(hallazgo -> hallazgo.severidad() == Severidad.ALTA)
                            .map(DiagnosticoDelDiagrama::redactar)
                            .toList());
                    errores.addAll(erroresDeLasLlamadas(empresaId, proceso.id()));
                    return errores;
                });
    }

    /**
     * PR 56: lo de las llamadas mira mas alla del diagrama: si el proceso llamado sigue vivo, y si siguiendo las
     * llamadas se vuelve a este proceso. Se revisa al publicar, que es cuando empieza a importar.
     */
    private List<String> erroresDeLasLlamadas(Long empresaId, Long procesoId) {
        List<ActividadRepository.Llamada> llamadas = actividadRepository.llamadasDeLaTienda(empresaId);
        List<String> errores = new ArrayList<>();
        Map<Long, List<Long>> grafo = new HashMap<>();
        for (ActividadRepository.Llamada llamada : llamadas) {
            grafo.computeIfAbsent(llamada.procesoId(), sinLlamadas -> new ArrayList<>()).add(llamada.llamadoId());
            if (llamada.procesoId().equals(procesoId) && !llamada.llamadoActivo()) {
                errores.add(CodigoDeDiagnostico.E20.codigo() + " Actividad \"" + llamada.actividad() + "\": llama a \""
                        + llamada.llamadoNombre() + "\", que esta dado de baja.");
            }
        }
        if (alcanza(grafo, procesoId)) {
            errores.add(CodigoDeDiagnostico.E21.codigo() + " Las actividades de llamada vuelven a este proceso: las "
                    + "llamadas no pueden ser circulares.");
        }
        return errores;
    }

    /** Si siguiendo las llamadas desde este proceso se vuelve a el. */
    private static boolean alcanza(Map<Long, List<Long>> grafo, Long procesoId) {
        Set<Long> vistos = new HashSet<>();
        Deque<Long> porVer = new ArrayDeque<>(grafo.getOrDefault(procesoId, List.of()));
        while (!porVer.isEmpty()) {
            Long actual = porVer.poll();
            if (actual.equals(procesoId)) {
                return true;
            }
            if (vistos.add(actual)) {
                porVer.addAll(grafo.getOrDefault(actual, List.of()));
            }
        }
        return false;
    }

    /** El codigo y el elemento van en el texto: quien recibe el 409 no tiene el diagnostico delante. */
    private static String redactar(HallazgoDiagnosticoResponse hallazgo) {
        return hallazgo.codigo() + " " + hallazgo.elemento() + ": " + hallazgo.problema();
    }
}
