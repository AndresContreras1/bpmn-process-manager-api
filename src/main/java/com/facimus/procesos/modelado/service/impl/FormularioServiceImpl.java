package com.facimus.procesos.modelado.service.impl;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.CamposInvalidosException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.formularios.EsquemaDelFormulario;
import com.facimus.procesos.common.formularios.Formulario;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.mapper.ActividadMapper;
import com.facimus.procesos.modelado.mapper.EventoMapper;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;
import com.facimus.procesos.modelado.service.FormularioService;

import lombok.RequiredArgsConstructor;

/**
 * PR 69: guardar el formulario de un nodo. Lo que no esta bien armado no se guarda (400 con cada propiedad), y un
 * formulario en un nodo que no llena una persona tampoco (409): el check de la base lo sostiene igual.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FormularioServiceImpl implements FormularioService {

    static final String SOLO_DE_USUARIO = "Solo una actividad de usuario lleva formulario: es la que hace una persona.";
    static final String SOLO_DE_INICIO = "Solo un evento de inicio simple lleva formulario: es el que abre una "
            + "persona; los de mensaje y de temporizador se abren solos.";

    private final ActividadRepository actividadRepository;
    private final EventoRepository eventoRepository;
    private final HistorialCambioService historialCambioService;
    private final ActividadMapper actividadMapper;
    private final EventoMapper eventoMapper;

    @Override
    @Transactional
    public ActividadResponse deLaActividad(Long empresaId, Long usuarioId, Long actividadId, Formulario formulario,
            Long version) {
        Actividad actividad = actividadRepository.findByIdAndEmpresaId(actividadId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Actividad no encontrada."));
        actividad.verificarVersion(version);
        Optional<Formulario> nuevo = revisado(formulario,
                actividad.getTipoActividad() == TipoActividad.USUARIO, SOLO_DE_USUARIO);
        guardar(empresaId, usuarioId, actividad, nuevo, "de la actividad");
        return actividadMapper.toResponse(actividadRepository.saveAndFlush(actividad));
    }

    @Override
    @Transactional
    public EventoResponse delEvento(Long empresaId, Long usuarioId, Long eventoId, Formulario formulario,
            Long version) {
        Evento evento = eventoRepository.findByIdAndEmpresaId(eventoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Evento no encontrado."));
        evento.verificarVersion(version);
        Optional<Formulario> nuevo = revisado(formulario, evento.getTipoEvento() == TipoEvento.INICIO,
                SOLO_DE_INICIO);
        guardar(empresaId, usuarioId, evento, nuevo, "del evento");
        return eventoMapper.toResponse(eventoRepository.saveAndFlush(evento));
    }

    /**
     * El formulario que se guarda, o vacio si no tiene campos. Quitarlo se puede siempre; ponerlo, solo donde lo llena
     * una persona y bien armado.
     */
    private static Optional<Formulario> revisado(Formulario formulario, boolean loLlenaUnaPersona, String porQueNo) {
        Optional<Formulario> lleno = Formulario.siTieneCampos(formulario);
        if (lleno.isEmpty()) {
            return lleno;
        }
        if (!loLlenaUnaPersona) {
            throw new ReglaNegocioException(porQueNo);
        }
        Map<String, String> problemas = EsquemaDelFormulario.problemas(lleno.orElseThrow());
        if (!problemas.isEmpty()) {
            throw new CamposInvalidosException("El formulario no está bien armado.", problemas);
        }
        return lleno;
    }

    private void guardar(Long empresaId, Long usuarioId, NodoFlujo nodo, Optional<Formulario> nuevo, String de) {
        nodo.setFormulario(nuevo.orElse(null));
        historialCambioService.registrar(empresaId, usuarioId, nodo.getLane().getPool().getProceso(),
                "Formulario " + de + " \"" + nodo.getNombre() + "\" " + (nuevo.isPresent() ? "guardado." : "quitado."));
    }
}
