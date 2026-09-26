package com.facimus.procesos.ejecucion.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.mapper.CasoMapper;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.service.TareaService;
import com.facimus.procesos.gestion.service.MembresiaRolService;
import com.facimus.procesos.gestion.service.UsuarioService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * La bandeja de tareas. Completar una tarea es lo que mas veces mueve un caso, asi que es donde mas se nota D3: se
 * bloquea el caso antes de leer la tarea, y solo despues se mira si sigue en espera.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TareaServiceImpl implements TareaService {

    private final ActividadCasoRepository actividadCasoRepository;
    private final CasoRepository casoRepository;
    private final UsuarioService usuarioService;
    private final MembresiaRolService membresiaRolService;
    private final GrafosDeVersion grafos;
    private final MotorDeProcesos motor;
    private final RelojDeLaTienda reloj;
    private final Bitacora bitacora;
    private final CasoMapper casoMapper;
    private final JsonMapper json;

    @Override
    public PageResponse<TareaResponse> bandeja(Long empresaId, Long usuarioId, boolean mias, Long rolProcesoId,
            Long procesoId, EstadoActividadCaso estado, Pageable pagina) {
        EstadoActividadCaso buscado = estado == null ? EstadoActividadCaso.EN_ESPERA : estado;
        if (!mias) {
            return PageResponse.from(actividadCasoRepository
                    .bandejaPorRol(empresaId, rolProcesoId, procesoId, buscado, pagina)
                    .map(casoMapper::toTarea));
        }
        List<Long> roles = membresiaRolService.idsDeLosRolesDe(empresaId, usuarioId);
        if (roles.isEmpty()) {
            // Sin roles no hay bandeja propia, y preguntar "in ()" no es una consulta que la base acepte.
            return PageResponse.from(Page.<ActividadCaso>empty(pagina).map(casoMapper::toTarea));
        }
        if (rolProcesoId != null && !roles.contains(rolProcesoId)) {
            return PageResponse.from(Page.<ActividadCaso>empty(pagina).map(casoMapper::toTarea));
        }
        List<Long> acotados = rolProcesoId == null ? roles : List.of(rolProcesoId);
        return PageResponse.from(actividadCasoRepository
                .bandejaDeMisRoles(empresaId, acotados, procesoId, buscado, pagina)
                .map(casoMapper::toTarea));
    }

    @Override
    public TareaResponse obtener(Long empresaId, Long tareaId) {
        return casoMapper.toTarea(exigirTarea(empresaId, tareaId));
    }

    /**
     * R-49: una tarea se completa una vez. El caso se bloquea antes de leer la tarea, no despues: si se leyera
     * primero, el segundo hilo se quedaria con el estado que vio antes de esperar y la completaria otra vez.
     */
    @Override
    @Transactional
    public TareaResponse completar(Long empresaId, Long usuarioId, Long tareaId, Map<String, Object> datos) {
        Caso caso = bloquearElCasoDe(empresaId, tareaId);
        ActividadCaso tarea = exigirTarea(empresaId, tareaId);
        if (tarea.getEstado() != EstadoActividadCaso.EN_ESPERA) {
            throw new ReglaNegocioException("La tarea ya fue completada.");
        }
        if (caso.getEstado() != EstadoCaso.ABIERTO) {
            throw new ReglaNegocioException("El caso ya está cerrado.");
        }

        if (datos != null && !datos.isEmpty()) {
            VariablesDelCaso variables = VariablesDelCaso.de(caso, json);
            variables.ponerDeLaTarea(tarea.getNodoNombre(), datos);
            caso.setVariables(variables.comoJson());
            tarea.setDatosSalida(casoMapper.aJson(datos));
        }
        Momento momento = new Momento(reloj.ahora(empresaId), usuarioId);
        tarea.setEstado(EstadoActividadCaso.COMPLETADA);
        tarea.setTickFin(momento.tick());
        actividadCasoRepository.save(tarea);
        bitacora.anotar(caso, momento.tick(), TipoEventoCaso.TAREA_COMPLETADA,
                "\"" + tarea.getNodoNombre() + "\" se completo.", usuarioId);

        motor.seguirDesde(caso, grafos.del(empresaId, caso.getVersionProceso()), tarea.getNodoId(), momento);
        return casoMapper.toTarea(tarea);
    }

    /**
     * Reservar una tarea es una nota para el equipo: la bandeja la sigue viendo el rol entero y completarla no
     * exige tenerla reservada (D13).
     */
    @Override
    @Transactional
    public TareaResponse asignar(Long empresaId, Long tareaId, Long usuarioId) {
        ActividadCaso tarea = exigirTarea(empresaId, tareaId);
        if (tarea.getEstado() != EstadoActividadCaso.EN_ESPERA) {
            throw new ReglaNegocioException("La tarea ya fue completada.");
        }
        if (usuarioId != null) {
            usuarioService.obtener(empresaId, usuarioId);
        }
        tarea.setAsignadoA(usuarioId);
        return casoMapper.toTarea(actividadCasoRepository.save(tarea));
    }

    /** Solo una actividad de usuario es una tarea; los demas pasos del caso los mueve el motor, no una persona. */
    private ActividadCaso exigirTarea(Long empresaId, Long tareaId) {
        ActividadCaso tarea = actividadCasoRepository.findByIdAndEmpresaId(tareaId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tarea no encontrada."));
        if (!tarea.esTarea()) {
            throw new RecursoNoEncontradoException("Tarea no encontrada.");
        }
        return tarea;
    }

    private Caso bloquearElCasoDe(Long empresaId, Long tareaId) {
        Long casoId = actividadCasoRepository.casoDe(tareaId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tarea no encontrada."));
        return casoRepository.bloquear(casoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tarea no encontrada."));
    }
}
