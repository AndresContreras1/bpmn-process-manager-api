package com.facimus.procesos.modelado.service.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.DiagnosticoDelModelo;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.HallazgoDiagnosticoResponse;
import com.facimus.procesos.modelado.model.CodigoDeDiagnostico;
import com.facimus.procesos.modelado.model.Severidad;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.ActividadRepository.Llamada;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

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
    private final ProcesoRepository procesoRepository;
    private final JsonMapper json;

    /** El mismo span que el diagnostico que pide quien edita, marcado como el que decide si se publica. */
    @Override
    public List<String> errores(Long empresaId, ProcesoResponse proceso) {
        return observar("publicacion", proceso.id(), () -> {
            List<Llamada> llamadas = actividadRepository.llamadasDeLaTienda(empresaId);
            List<Llamada> propias = llamadas.stream().filter(llamada -> llamada.procesoId().equals(proceso.id()))
                    .toList();
            return errores(armado.armar(proceso, false, empresaId), proceso.id(), llamadas, propias);
        });
    }

    /**
     * PR 64: lo congelado se revisa como estaba, y lo que mira mas alla de el se mira hoy: si los procesos a los que
     * llama siguen vivos, y si siguiendo las llamadas de los demas procesos de hoy se vuelve a este.
     */
    @Override
    public List<String> errores(Long empresaId, Long procesoId, String definicion) {
        DiagramaResponse diagrama = json.readValue(definicion, DiagramaResponse.class);
        return observar("aprobacion", procesoId, () -> errores(diagrama, procesoId,
                actividadRepository.llamadasDeLaTienda(empresaId), llamadasCongeladas(empresaId, procesoId,
                        diagrama)));
    }

    private List<String> observar(String uso, Long procesoId, Supplier<List<String>> diagnostico) {
        return Observation.createNotStarted("procesos.diagnostico", observaciones)
                .contextualName("diagnosticar antes de publicar")
                .lowCardinalityKeyValue("uso", uso)
                .highCardinalityKeyValue("proceso.id", String.valueOf(procesoId))
                .observe(diagnostico);
    }

    private static List<String> errores(DiagramaResponse diagrama, Long procesoId, List<Llamada> llamadas,
            List<Llamada> propias) {
        List<String> errores = new ArrayList<>(DiagnosticoServiceImpl.revisar(diagrama).hallazgos().stream()
                .filter(hallazgo -> hallazgo.severidad() == Severidad.ALTA)
                .map(DiagnosticoDelDiagrama::redactar)
                .toList());
        errores.addAll(erroresDeLasLlamadas(procesoId, llamadas, propias));
        return errores;
    }

    /** Las actividades de llamada del diagrama congelado, con el proceso llamado como esta hoy. */
    private List<Llamada> llamadasCongeladas(Long empresaId, Long procesoId, DiagramaResponse diagrama) {
        List<ActividadResponse> llamadoras = diagrama.actividades().stream()
                .filter(actividad -> actividad.procesoLlamadoId() != null).toList();
        if (llamadoras.isEmpty()) {
            return List.of();
        }
        Map<Long, Proceso> llamados = procesoRepository.findAllByIdInAndEmpresaId(llamadoras.stream()
                .map(ActividadResponse::procesoLlamadoId).collect(Collectors.toSet()), empresaId).stream()
                .collect(Collectors.toMap(Proceso::getId, Function.identity()));
        return llamadoras.stream().map(actividad -> {
            Proceso llamado = llamados.get(actividad.procesoLlamadoId());
            // Uno que ya no se encuentra cuenta como dado de baja
            return new Llamada(procesoId, actividad.procesoLlamadoId(), llamado == null ? "" : llamado.getNombre(),
                    llamado != null && llamado.isActivo(), actividad.nombre());
        }).toList();
    }

    /**
     * PR 56: lo de las llamadas mira mas alla del diagrama: si el proceso llamado sigue vivo, y si siguiendo las
     * llamadas se vuelve a este proceso. Se revisa al publicar, que es cuando empieza a importar. Las llamadas del
     * proceso que se revisa son las propias; las de los demas, las de la tienda.
     */
    private static List<String> erroresDeLasLlamadas(Long procesoId, List<Llamada> llamadas, List<Llamada> propias) {
        List<String> errores = new ArrayList<>();
        Map<Long, List<Long>> grafo = new HashMap<>();
        for (Llamada llamada : llamadas) {
            if (!llamada.procesoId().equals(procesoId)) {
                grafo.computeIfAbsent(llamada.procesoId(), sinLlamadas -> new ArrayList<>()).add(llamada.llamadoId());
            }
        }
        for (Llamada llamada : propias) {
            grafo.computeIfAbsent(procesoId, sinLlamadas -> new ArrayList<>()).add(llamada.llamadoId());
            if (!llamada.llamadoActivo()) {
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
