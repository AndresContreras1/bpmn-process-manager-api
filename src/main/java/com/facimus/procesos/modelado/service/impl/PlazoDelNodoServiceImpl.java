package com.facimus.procesos.modelado.service.impl;

import java.util.Map;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.CamposInvalidosException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.mapper.ActividadMapper;
import com.facimus.procesos.modelado.mapper.EventoMapper;
import com.facimus.procesos.modelado.model.AccionDeEscalamiento;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.UnidadDePlazo;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;
import com.facimus.procesos.modelado.service.PlazoDelNodoService;

import lombok.RequiredArgsConstructor;

/**
 * PR 71: guardar el plazo de una tarea o de un caso y lo que hace un temporizador de borde al saltar. Lo que no esta
 * bien armado es un 400 en su propiedad; lo que va en un nodo que no lo lleva, un 409, y los checks de la base lo
 * sostienen igual. Es una edicion del borrador: va al historial del proceso, como las demas.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlazoDelNodoServiceImpl implements PlazoDelNodoService {

    static final String SOLO_DE_USUARIO = "Solo una actividad de usuario lleva plazo: es la que espera a una persona.";
    static final String SOLO_DE_INICIO = "Solo un evento de inicio lleva el plazo del caso: es el que lo abre.";
    static final String SOLO_DE_BORDE = "Solo un temporizador de borde que no interrumpe escala: el que interrumpe ya "
            + "saca a la tarea por su camino.";
    static final String REASIGNA_UNA_TAREA = "Solo se reasigna una tarea de usuario: el borde va pegado a otra cosa.";
    static final String PLAZO_MAL_ARMADO = "El plazo no está bien armado.";
    static final String ESCALAMIENTO_MAL_ARMADO = "El escalamiento no está bien armado.";
    static final String SIN_UNIDAD = "Con una cantidad hay que decir la unidad: HORAS o DIAS_HABILES.";
    static final String SIN_CANTIDAD = "Con una unidad hay que decir la cantidad.";
    private static final String CANTIDAD = "cantidad";

    private final ActividadRepository actividadRepository;
    private final EventoRepository eventoRepository;
    private final UsuarioService usuarioService;
    private final HistorialCambioService historialCambioService;
    private final ActividadMapper actividadMapper;
    private final EventoMapper eventoMapper;

    @Override
    @Transactional
    public ActividadResponse deLaActividad(Long empresaId, Long usuarioId, Long actividadId, Integer cantidad,
            UnidadDePlazo unidad, Long version) {
        Actividad actividad = actividadRepository.findByIdAndEmpresaId(actividadId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Actividad no encontrada."));
        actividad.verificarVersion(version);
        boolean conPlazo = revisado(cantidad, unidad);
        if (conPlazo && actividad.getTipoActividad() != TipoActividad.USUARIO) {
            throw new ReglaNegocioException(SOLO_DE_USUARIO);
        }
        poner(actividad, conPlazo ? cantidad : null, unidad);
        historialCambioService.registrar(empresaId, usuarioId, actividad.getLane().getPool().getProceso(),
                "Plazo de la actividad \"" + actividad.getNombre() + "\": " + comoSeLee(conPlazo, cantidad, unidad)
                        + ".");
        return actividadMapper.toResponse(actividadRepository.saveAndFlush(actividad));
    }

    @Override
    @Transactional
    public EventoResponse delEvento(Long empresaId, Long usuarioId, Long eventoId, Integer cantidad,
            UnidadDePlazo unidad, Long version) {
        Evento evento = evento(empresaId, eventoId, version);
        boolean conPlazo = revisado(cantidad, unidad);
        if (conPlazo && !evento.getTipoEvento().empiezaElProceso()) {
            throw new ReglaNegocioException(SOLO_DE_INICIO);
        }
        poner(evento, conPlazo ? cantidad : null, unidad);
        historialCambioService.registrar(empresaId, usuarioId, evento.getLane().getPool().getProceso(),
                "Plazo del caso en el evento \"" + evento.getNombre() + "\": "
                        + comoSeLee(conPlazo, cantidad, unidad) + ".");
        return eventoMapper.toResponse(eventoRepository.saveAndFlush(evento));
    }

    @Override
    @Transactional
    public EventoResponse escalamiento(Long empresaId, Long usuarioId, Long eventoId, AccionDeEscalamiento accion,
            Long supervisorId, Long version) {
        Evento evento = evento(empresaId, eventoId, version);
        if (accion != null) {
            if (evento.getTipoEvento() != TipoEvento.TEMPORIZADOR_BORDE
                    || !Boolean.FALSE.equals(evento.getInterrumpe())) {
                throw new ReglaNegocioException(SOLO_DE_BORDE);
            }
            if (accion == AccionDeEscalamiento.REASIGNAR && !esTareaDeUsuario(evento.getAdjuntoA())) {
                throw new ReglaNegocioException(REASIGNA_UNA_TAREA);
            }
            if (supervisorId != null) {
                PersonaQueCompleta.exigir(usuarioService, empresaId, supervisorId, ESCALAMIENTO_MAL_ARMADO,
                        "usuarioId");
            }
        }
        evento.setEscalamiento(accion);
        evento.setEscalarA(accion == null ? null : supervisorId);
        historialCambioService.registrar(empresaId, usuarioId, evento.getLane().getPool().getProceso(),
                "Escalamiento del evento \"" + evento.getNombre() + "\": " + comoSeLee(accion, supervisorId) + ".");
        return eventoMapper.toResponse(eventoRepository.saveAndFlush(evento));
    }

    private Evento evento(Long empresaId, Long eventoId, Long version) {
        Evento evento = eventoRepository.findByIdAndEmpresaId(eventoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Evento no encontrado."));
        evento.verificarVersion(version);
        return evento;
    }

    /**
     * Si llega un plazo que guardar. Cantidad y unidad van juntas, o ninguna de las dos; la cantidad, entre una y el
     * tope de su unidad.
     */
    private static boolean revisado(Integer cantidad, UnidadDePlazo unidad) {
        if (cantidad == null && unidad == null) {
            return false;
        }
        if (unidad == null) {
            throw new CamposInvalidosException(PLAZO_MAL_ARMADO, Map.of("unidad", SIN_UNIDAD));
        }
        if (cantidad == null) {
            throw new CamposInvalidosException(PLAZO_MAL_ARMADO, Map.of(CANTIDAD, SIN_CANTIDAD));
        }
        if (!unidad.admite(cantidad)) {
            throw new CamposInvalidosException(PLAZO_MAL_ARMADO, Map.of(CANTIDAD,
                    "En " + unidad + " va de 1 a " + unidad.maximo() + "."));
        }
        return true;
    }

    private static void poner(NodoFlujo nodo, Integer cantidad, UnidadDePlazo unidad) {
        nodo.setPlazoCantidad(cantidad);
        nodo.setPlazoUnidad(cantidad == null ? null : unidad);
    }

    /** Si el borde va pegado a una tarea de usuario: la unica que se le puede pasar a otra persona. */
    private static boolean esTareaDeUsuario(NodoFlujo adjunto) {
        return adjunto != null && Hibernate.unproxy(adjunto) instanceof Actividad actividad
                && actividad.getTipoActividad() == TipoActividad.USUARIO;
    }

    private static String comoSeLee(boolean conPlazo, Integer cantidad, UnidadDePlazo unidad) {
        if (!conPlazo) {
            return "sin plazo";
        }
        return cantidad + (unidad == UnidadDePlazo.HORAS ? " horas" : " dias habiles");
    }

    private static String comoSeLee(AccionDeEscalamiento accion, Long supervisorId) {
        if (accion == null) {
            return "solo abre su camino";
        }
        String supervisor = supervisorId == null ? "dueno del proceso" : "usuario " + supervisorId;
        return (accion == AccionDeEscalamiento.AVISAR ? "avisa al " : "reasigna al ") + supervisor;
    }
}
