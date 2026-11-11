package com.facimus.procesos.modelado.service.impl;

import java.util.List;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.mapper.ActividadMapper;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.service.SubprocesoService;

import lombok.RequiredArgsConstructor;

/**
 * PR 56: los flujos no cruzan el borde de un subproceso, asi que un nodo con flujos no cambia de contenedor. Lo de
 * dentro esta en la lane de su subproceso, y un evento de borde va donde vaya su actividad.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubprocesoServiceImpl implements SubprocesoService {

    private final NodoFlujoRepository nodoFlujoRepository;
    private final ActividadRepository actividadRepository;
    private final ArcoRepository arcoRepository;
    private final ProcesoRepository procesoRepository;
    private final ActividadMapper actividadMapper;
    private final HistorialCambioService historialCambioService;
    private final ElementosPegados pegados;

    @Override
    @Transactional
    public void mover(Long empresaId, Long usuarioId, Long nodoId, Long padreId) {
        NodoFlujo nodo = nodoFlujoRepository.findByIdAndEmpresaId(nodoId, empresaId)
                .map(encontrado -> (NodoFlujo) Hibernate.unproxy(encontrado))
                .orElseThrow(() -> new RecursoNoEncontradoException("Nodo no encontrado."));
        if (nodo instanceof Evento evento && evento.getTipoEvento().esDeBorde()) {
            throw new ReglaNegocioException("Un evento de borde va donde vaya su actividad.");
        }
        if (arcoRepository.tieneArcos(nodoId, empresaId)) {
            throw new ReglaNegocioException("Un nodo con flujos no cambia de contenedor: los flujos no cruzan el borde "
                    + "de un subproceso.");
        }
        // Se consulta antes de tocar la entidad: una consulta despues la escribiria y gastaria una version de mas
        List<NodoFlujo> conEl = pegados.pegadosA(empresaId, nodoId);
        NodoFlujo padre = padreId == null ? null : subproceso(empresaId, padreId, nodo, conEl);
        nodo.setPadre(padre);
        if (padre != null) {
            nodo.setLane(padre.getLane());
            conEl.forEach(pegado -> pegado.setLane(padre.getLane()));
        }
        conEl.stream().filter(pegado -> pegado instanceof Evento evento && evento.getTipoEvento().esDeBorde()
                && evento.getAdjuntoA() != null && evento.getAdjuntoA().getId().equals(nodoId))
                .forEach(borde -> borde.setPadre(padre));
        historialCambioService.registrar(empresaId, usuarioId, nodo.getLane().getPool().getProceso(),
                "Nodo \"" + nodo.getNombre() + "\" " + (padre == null ? "sacado de su subproceso."
                        : "movido al subproceso \"" + padre.getNombre() + "\"."));
        nodoFlujoRepository.saveAndFlush(nodo);
    }

    /** El contenedor pedido: un subproceso expandido del mismo pool, que no sea el nodo ni algo de dentro de el. */
    private NodoFlujo subproceso(Long empresaId, Long padreId, NodoFlujo nodo, List<NodoFlujo> conEl) {
        NodoFlujo padre = nodoFlujoRepository.findByIdAndEmpresaId(padreId, empresaId)
                .map(encontrado -> (NodoFlujo) Hibernate.unproxy(encontrado))
                .filter(candidato -> candidato instanceof Actividad actividad
                        && actividad.getTipoActividad() == TipoActividad.SUBPROCESO)
                .orElseThrow(() -> new ReglaNegocioException("Un nodo solo va dentro de un subproceso expandido."));
        boolean dentroDeSiMismo = padreId.equals(nodo.getId())
                || conEl.stream().anyMatch(pegado -> pegado.getId().equals(padreId));
        if (dentroDeSiMismo) {
            throw new ReglaNegocioException("Un subproceso no se puede meter dentro de si mismo.");
        }
        if (!padre.getLane().getPool().getId().equals(nodo.getLane().getPool().getId())) {
            throw new ReglaNegocioException("El subproceso esta en otro pool.");
        }
        return padre;
    }

    @Override
    @Transactional
    public ActividadResponse llamarA(Long empresaId, Long usuarioId, Long actividadId, Long procesoId, Long version) {
        Actividad actividad = actividadRepository.findByIdAndEmpresaId(actividadId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Actividad no encontrada."));
        actividad.verificarVersion(version);
        if (actividad.getTipoActividad() != TipoActividad.LLAMADA) {
            throw new ReglaNegocioException("Solo una actividad de llamada corre otro proceso.");
        }
        Proceso propio = actividad.getLane().getPool().getProceso();
        Proceso llamado = procesoId == null ? null : procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId,
                empresaId).orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
        if (llamado != null && llamado.getId().equals(propio.getId())) {
            throw new ReglaNegocioException("Una actividad no puede llamar a su propio proceso.");
        }
        actividad.setProcesoLlamado(llamado);
        historialCambioService.registrar(empresaId, usuarioId, propio, "Actividad \"" + actividad.getNombre() + "\" "
                + (llamado == null ? "sin proceso que llamar." : "llama a \"" + llamado.getNombre() + "\"."));
        return actividadMapper.toResponse(actividadRepository.saveAndFlush(actividad));
    }
}
