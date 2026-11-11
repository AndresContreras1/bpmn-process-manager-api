package com.facimus.procesos.modelado.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.AsociacionResponse;
import com.facimus.procesos.modelado.mapper.AsociacionMapper;
import com.facimus.procesos.modelado.model.Asociacion;
import com.facimus.procesos.modelado.model.DireccionAsociacion;
import com.facimus.procesos.modelado.model.TipoArtefacto;
import com.facimus.procesos.modelado.model.TipoAsociacion;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.ArtefactoRepository;
import com.facimus.procesos.modelado.repository.AsociacionRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;
import com.facimus.procesos.modelado.repository.GatewayRepository;
import com.facimus.procesos.modelado.service.AsociacionService;

import lombok.RequiredArgsConstructor;

/**
 * PR 55: una asociacion une un artefacto con algo del diagrama; una asociacion de datos lleva un dato a una
 * actividad o lo saca de ella. Los extremos se buscan por bpmn_id entre lo que el proceso tiene hoy.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AsociacionServiceImpl implements AsociacionService {

    /** Que es cada extremo, para saber si la asociacion tiene sentido. */
    private enum Clase {
        ACTIVIDAD, NODO, FLUJO, ARTEFACTO, DATO
    }

    private final AsociacionRepository asociacionRepository;
    private final ProcesoRepository procesoRepository;
    private final ActividadRepository actividadRepository;
    private final GatewayRepository gatewayRepository;
    private final EventoRepository eventoRepository;
    private final ArcoRepository arcoRepository;
    private final ArtefactoRepository artefactoRepository;
    private final AsociacionMapper asociacionMapper;
    private final HistorialCambioService historialCambioService;

    @Override
    @Transactional
    public AsociacionResponse crear(Long empresaId, Long usuarioId, Long procesoId, TipoAsociacion tipo,
            String origen, String destino, DireccionAsociacion direccion) {
        Proceso proceso = procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
        if (origen.equals(destino)) {
            throw new ReglaNegocioException("Una asociacion une dos elementos distintos.");
        }
        Map<String, Clase> elementos = elementos(empresaId, procesoId);
        Clase desde = extremo(elementos, origen);
        Clase hasta = extremo(elementos, destino);
        exigirQueTengaSentido(tipo, desde, hasta);
        boolean repetida = asociacionRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId)
                .stream()
                .anyMatch(una -> una.getTipo() == tipo && una.getOrigen().equals(origen)
                        && una.getDestino().equals(destino));
        if (repetida) {
            throw new ReglaNegocioException("Esos dos elementos ya estan unidos asi.");
        }
        Asociacion asociacion = asociacionRepository.save(Asociacion.builder()
                .empresa(proceso.getEmpresa())
                .proceso(proceso)
                .tipo(tipo)
                .origen(origen)
                .destino(destino)
                // La direccion es de la asociacion; una de datos ya dice hacia donde va el dato
                .direccion(tipo == TipoAsociacion.ASOCIACION && direccion != null ? direccion
                        : DireccionAsociacion.NINGUNA)
                .build());
        historialCambioService.registrar(empresaId, usuarioId, proceso, "Asociacion de " + origen + " a " + destino
                + " agregada.");
        return asociacionMapper.toResponse(asociacion);
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long usuarioId, Long asociacionId) {
        Asociacion asociacion = asociacionRepository.findByIdAndEmpresaId(asociacionId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Asociacion no encontrada."));
        asociacionRepository.delete(asociacion);
        historialCambioService.registrar(empresaId, usuarioId, asociacion.getProceso(), "Asociacion de "
                + asociacion.getOrigen() + " a " + asociacion.getDestino() + " eliminada.");
    }

    @Override
    public List<AsociacionResponse> listar(Long empresaId, Long procesoId) {
        if (!procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)) {
            throw new RecursoNoEncontradoException("Proceso no encontrado.");
        }
        return asociacionMapper.toResponses(asociacionRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(
                procesoId, empresaId));
    }

    /**
     * Una asociacion lleva un artefacto en alguno de sus lados; la de entrada va de un dato a una actividad y la de
     * salida de una actividad a un dato, como en BPMN.
     */
    private static void exigirQueTengaSentido(TipoAsociacion tipo, Clase desde, Clase hasta) {
        boolean valida = switch (tipo) {
            case ASOCIACION -> esArtefacto(desde) || esArtefacto(hasta);
            case ENTRADA_DATOS -> desde == Clase.DATO && hasta == Clase.ACTIVIDAD;
            case SALIDA_DATOS -> desde == Clase.ACTIVIDAD && hasta == Clase.DATO;
        };
        if (!valida) {
            throw new ReglaNegocioException(switch (tipo) {
                case ASOCIACION -> "Una asociacion une un artefacto con otro elemento del diagrama.";
                case ENTRADA_DATOS -> "Una entrada de datos va de un objeto de datos o un almacen a una actividad.";
                case SALIDA_DATOS -> "Una salida de datos va de una actividad a un objeto de datos o un almacen.";
            });
        }
    }

    private static boolean esArtefacto(Clase clase) {
        return clase == Clase.ARTEFACTO || clase == Clase.DATO;
    }

    private static Clase extremo(Map<String, Clase> elementos, String bpmnId) {
        Clase clase = elementos.get(bpmnId);
        if (clase == null) {
            throw new ReglaNegocioException("El proceso no tiene ningun elemento " + bpmnId + ".");
        }
        return clase;
    }

    private Map<String, Clase> elementos(Long empresaId, Long procesoId) {
        Map<String, Clase> elementos = new HashMap<>();
        actividadRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId)
                .forEach(nodo -> elementos.put(nodo.getBpmnId(), Clase.ACTIVIDAD));
        gatewayRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId)
                .forEach(nodo -> elementos.put(nodo.getBpmnId(), Clase.NODO));
        eventoRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId)
                .forEach(nodo -> elementos.put(nodo.getBpmnId(), Clase.NODO));
        arcoRepository.findAllByPool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId)
                .forEach(arco -> elementos.put(arco.getBpmnId(), Clase.FLUJO));
        artefactoRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId)
                .forEach(artefacto -> elementos.put(artefacto.getBpmnId(),
                        artefacto.getTipo() == TipoArtefacto.OBJETO_DATOS
                                || artefacto.getTipo() == TipoArtefacto.ALMACEN_DATOS ? Clase.DATO
                                : Clase.ARTEFACTO));
        return elementos;
    }
}
