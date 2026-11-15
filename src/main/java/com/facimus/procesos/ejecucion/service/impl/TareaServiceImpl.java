package com.facimus.procesos.ejecucion.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.ejecucion.dto.response.CargaDeTrabajoResponse;
import com.facimus.procesos.ejecucion.dto.response.FormularioDeTareaResponse;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.mapper.CasoMapper;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Bandeja;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EstadoDelegacion;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.service.TareaService;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.MembresiaRolService;
import com.facimus.procesos.modelado.dto.response.FormularioResponse;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * La bandeja de tareas. Completar una tarea es lo que mas veces mueve un caso, asi que es donde mas se nota D3: se
 * bloquea el caso antes de leer la tarea, y solo despues se mira si sigue en espera y quien la tiene.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TareaServiceImpl implements TareaService {

    static final String YA_COMPLETADA = "La tarea ya fue completada.";
    static final String DE_OTRA_PERSONA = "La tarea la tiene otra persona: solo ella la completa. Quien reparte el "
            + "trabajo se la puede reasignar.";
    static final String NO_ES_DE_SU_ROL = "La tarea espera en la bandeja de un rol al que no perteneces: la toma "
            + "alguien de ese rol o un administrador.";
    /** PR 70: lo que la bitacora de la tienda anota cuando un administrador completa la tarea de otra persona. */
    static final String COMPLETADA_POR_OTRO = "TAREA_COMPLETADA_POR_OTRO";
    static final String RECURSO = "TAREA";

    private final ActividadCasoRepository actividadCasoRepository;
    private final Tareas tareas;
    private final MembresiaRolService membresiaRolService;
    private final ConfiguracionTiendaService configuracionTiendaService;
    private final PersonasDeLaTarea personas;
    private final GrafosDeVersion grafos;
    private final MotorDeProcesos motor;
    private final RelojDeLaTienda reloj;
    private final Bitacora bitacora;
    private final Auditoria auditoria;
    private final CasoMapper casoMapper;
    private final JsonMapper json;
    private final FormulariosDelCaso formularios;
    private final Plazos plazos;

    @Override
    @SuppressWarnings("java:S107") // cada filtro de la bandeja, por su nombre
    public PageResponse<TareaResponse> bandeja(Long empresaId, Long usuarioId, Bandeja bandeja, Long rolProcesoId,
            Long procesoId, EstadoActividadCaso estado, Pageable pagina) {
        return laBandeja(empresaId, usuarioId, bandeja, rolProcesoId, procesoId, estado, pagina);
    }

    /** La de antes del PR 70; va aqui y no en la interfaz para que corra en la transaccion de solo lectura. */
    @Override
    @SuppressWarnings("java:S107") // cada filtro de la bandeja, por su nombre
    public PageResponse<TareaResponse> bandeja(Long empresaId, Long usuarioId, boolean mias, Long rolProcesoId,
            Long procesoId, EstadoActividadCaso estado, Pageable pagina) {
        return laBandeja(empresaId, usuarioId, mias ? Bandeja.DE_MIS_ROLES : Bandeja.TODAS, rolProcesoId, procesoId,
                estado, pagina);
    }

    @SuppressWarnings("java:S107") // cada filtro de la bandeja, por su nombre
    private PageResponse<TareaResponse> laBandeja(Long empresaId, Long usuarioId, Bandeja bandeja, Long rolProcesoId,
            Long procesoId, EstadoActividadCaso estado, Pageable pagina) {
        EstadoActividadCaso buscado = estado == null ? EstadoActividadCaso.EN_ESPERA : estado;
        Bandeja cual = bandeja == null ? Bandeja.TODAS : bandeja;
        if (cual != Bandeja.DE_MIS_ROLES) {
            return PageResponse.from(actividadCasoRepository
                    .bandejaPorRol(empresaId, rolProcesoId, procesoId, buscado,
                            cual == Bandeja.MIAS ? usuarioId : null, cual == Bandeja.SIN_ASIGNAR, pagina)
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
        return casoMapper.toTarea(tareas.exigir(empresaId, tareaId));
    }

    /**
     * R-49: una tarea se completa una vez. El caso se bloquea antes de leer la tarea, no despues: si se leyera
     * primero, el segundo hilo se quedaria con el estado que vio antes de esperar y la completaria otra vez.
     */
    @Override
    @Transactional
    public TareaResponse completar(Long empresaId, Long usuarioId, Long tareaId, Map<String, Object> datos) {
        Caso caso = tareas.bloquearSuCaso(empresaId, tareaId);
        ActividadCaso tarea = tareas.exigir(empresaId, tareaId);
        if (tarea.getEstado() != EstadoActividadCaso.EN_ESPERA) {
            throw new ReglaNegocioException(YA_COMPLETADA);
        }
        if (caso.getEstado() != EstadoCaso.ABIERTO) {
            throw new ReglaNegocioException(Tareas.CASO_CERRADO);
        }
        boolean laToma = tarea.getAsignadoA() == null;
        Long porQuien = quienLaTiene(empresaId, usuarioId, tarea);

        GrafoDeVersion grafo = grafos.del(empresaId, caso.getVersionProceso());
        VariablesDelCaso variables = VariablesDelCaso.de(caso, json);
        Map<String, Object> valores = formularios.alCompletar(empresaId, caso.getProceso().getId(), grafo,
                tarea.getNodoId(), caso.getModo(), datos, variables);
        if (!valores.isEmpty()) {
            variables.ponerDeLaTarea(tarea.getNodoNombre(), valores);
            caso.setVariables(variables.comoJson());
            tarea.setDatosSalida(casoMapper.aJson(valores));
        }
        Momento momento = new Momento(reloj.ahora(empresaId), usuarioId);
        tareas.cerrarDelegacion(tarea, EstadoDelegacion.COMPLETADA);
        tarea.setAsignadoA(porQuien);
        tarea.setEstado(EstadoActividadCaso.COMPLETADA);
        tarea.setTickFin(momento.tick());
        // PR 71: se completo a tiempo o tarde, y asi se queda para las metricas
        plazos.alCompletar(tarea, momento);
        actividadCasoRepository.save(tarea);
        if (laToma) {
            // La que no tenia nadie la toma quien la completa: queda igual que si la hubiera reclamado antes
            bitacora.anotar(caso, momento.tick(), TipoEventoCaso.TAREA_RECLAMADA,
                    "\"" + tarea.getNodoNombre() + "\" la tomo el usuario " + usuarioId + " al completarla.", usuarioId);
            auditoria.registrar(empresaId, usuarioId, AsignacionDeTareaServiceImpl.RECLAMADA, RECURSO, tareaId,
                    Map.of(AsignacionDeTareaServiceImpl.CASO_ID, caso.getId()));
        }
        if (porQuien.equals(usuarioId)) {
            bitacora.anotar(caso, momento.tick(), TipoEventoCaso.TAREA_COMPLETADA,
                    "\"" + tarea.getNodoNombre() + "\" se completo.", usuarioId);
        } else {
            bitacora.anotar(caso, momento.tick(), TipoEventoCaso.TAREA_COMPLETADA, "\"" + tarea.getNodoNombre()
                    + "\" se completo: la completo un administrador por el usuario " + porQuien + ", que la tenia.",
                    usuarioId);
            auditoria.registrar(empresaId, usuarioId, COMPLETADA_POR_OTRO, RECURSO, tareaId,
                    Map.of(AsignacionDeTareaServiceImpl.CASO_ID, caso.getId(), "laTenia", porQuien));
        }

        motor.seguirDesde(caso, grafo, tarea.getNodoId(), momento);
        return casoMapper.toTarea(tarea);
    }

    /**
     * PR 70: en nombre de quien se completa. La completa quien la tiene; la que no tiene nadie, quien la podria tomar,
     * que asi la toma; la de otra persona, solo un administrador de una tienda que lo deja.
     */
    private Long quienLaTiene(Long empresaId, Long usuarioId, ActividadCaso tarea) {
        Long laTiene = tarea.getAsignadoA();
        if (usuarioId.equals(laTiene)) {
            return laTiene;
        }
        UsuarioResponse quien = personas.quienActua(empresaId, usuarioId);
        if (laTiene == null) {
            if (!personas.esCandidata(empresaId, quien, tarea)) {
                throw new SinPermisoException(NO_ES_DE_SU_ROL);
            }
            return usuarioId;
        }
        if (PersonasDeLaTarea.administra(quien) && configuracionTiendaService.administradorCompletaPorOtro(empresaId)) {
            return laTiene;
        }
        throw new ReglaNegocioException(DE_OTRA_PERSONA);
    }

    /** Solo lee: el formulario viene de la version del caso, que no cambia, y las variables, de su fila. */
    @Override
    public FormularioDeTareaResponse formulario(Long empresaId, Long tareaId) {
        ActividadCaso tarea = tareas.exigir(empresaId, tareaId);
        Caso caso = tarea.getCaso();
        FormularioResponse formulario = grafos.del(empresaId, caso.getVersionProceso())
                .formularioDe(tarea.getNodoId()).orElse(null);
        VariablesDelCaso variables = VariablesDelCaso.de(caso, json);
        return new FormularioDeTareaResponse(tarea.getId(), caso.getId(), tarea.getNodoNombre(), formulario,
                FormulariosDelCaso.iniciales(formulario, variables), variables.paraLeer());
    }

    @Override
    public CargaDeTrabajoResponse carga(Long empresaId, Long procesoId) {
        return new CargaDeTrabajoResponse(actividadCasoRepository.sinAsignar(empresaId, procesoId),
                actividadCasoRepository.cargaPorPersona(empresaId, procesoId));
    }
}
