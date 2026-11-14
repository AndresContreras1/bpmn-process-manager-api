package com.facimus.procesos.modelado.service.impl;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.CamposInvalidosException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.mapper.ActividadMapper;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoAsignacion;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.service.ReglaDeAsignacionService;

import lombok.RequiredArgsConstructor;

/**
 * PR 70: guardar a quien le llega una tarea. Lo que no se puede cumplir no se guarda: una persona que no puede
 * completar tareas o una ruta que no es una variable son un 400 en su campo, y una regla en una actividad que no hace
 * una persona, un 409 (el check de la base lo sostiene igual).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReglaDeAsignacionServiceImpl implements ReglaDeAsignacionService {

    static final String SOLO_DE_USUARIO = "Solo una actividad de usuario se asigna: es la que espera a una persona.";
    static final String NO_ARMADA = "La asignación no está bien armada.";
    static final String SIN_PERSONA = "Con PERSONA hay que decir quién.";
    static final String NO_ES_DE_LA_TIENDA = "No es una persona de la organización.";
    static final String NO_COMPLETA = "No puede completar tareas: está desactivada o solo lee.";
    static final String SIN_CAMPO = "Con CAMPO hay que decir la variable del caso que tiene a la persona, como la "
            + "nombra una condición: aprobador, tarea.revisarPedido.aprobador.";
    private static final String USUARIO_ID = "usuarioId";

    private final ActividadRepository actividadRepository;
    private final UsuarioService usuarioService;
    private final HistorialCambioService historialCambioService;
    private final ActividadMapper actividadMapper;

    @Override
    @Transactional
    public ActividadResponse deLaActividad(Long empresaId, Long usuarioId, Long actividadId, TipoAsignacion tipo,
            Long asignadoId, String campo, Long version) {
        Actividad actividad = actividadRepository.findByIdAndEmpresaId(actividadId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Actividad no encontrada."));
        actividad.verificarVersion(version);
        boolean alRol = tipo == TipoAsignacion.ROL;
        if (!alRol && actividad.getTipoActividad() != TipoActividad.USUARIO) {
            throw new ReglaNegocioException(SOLO_DE_USUARIO);
        }
        actividad.setTipoAsignacion(alRol ? null : tipo);
        actividad.setAsignacionUsuarioId(tipo == TipoAsignacion.PERSONA ? personaQueCompleta(empresaId, asignadoId)
                : null);
        actividad.setAsignacionCampo(tipo == TipoAsignacion.CAMPO ? rutaDeVariable(campo) : null);
        historialCambioService.registrar(empresaId, usuarioId, actividad.getLane().getPool().getProceso(),
                "Asignación de la actividad \"" + actividad.getNombre() + "\": " + descripcion(tipo) + ".");
        return actividadMapper.toResponse(actividadRepository.saveAndFlush(actividad));
    }

    /** La persona fija tiene que poder hacer la tarea: activa, y administradora o editora. */
    private Long personaQueCompleta(Long empresaId, Long asignadoId) {
        if (asignadoId == null) {
            throw new CamposInvalidosException(NO_ARMADA, Map.of(USUARIO_ID, SIN_PERSONA));
        }
        UsuarioResponse persona;
        try {
            persona = usuarioService.obtener(empresaId, asignadoId);
        } catch (RecursoNoEncontradoException _) {
            throw new CamposInvalidosException(NO_ARMADA, Map.of(USUARIO_ID, NO_ES_DE_LA_TIENDA));
        }
        if (!persona.activo() || !persona.rolAcceso().completaTareas()) {
            throw new CamposInvalidosException(NO_ARMADA, Map.of(USUARIO_ID, NO_COMPLETA));
        }
        return asignadoId;
    }

    private static String rutaDeVariable(String campo) {
        String ruta = campo == null ? null : campo.strip();
        if (!TipoAsignacion.esRutaDeVariable(ruta)) {
            throw new CamposInvalidosException(NO_ARMADA, Map.of("campo", SIN_CAMPO));
        }
        return ruta;
    }

    private static String descripcion(TipoAsignacion tipo) {
        return switch (tipo) {
            case ROL -> "la bandeja del rol de su lane";
            case PERSONA -> "una persona fija";
            case INICIADOR -> "quien abre el caso";
            case CAMPO -> "la persona de una variable del caso";
        };
    }
}
