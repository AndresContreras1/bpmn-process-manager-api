package com.facimus.procesos.modelado.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.mapper.ArcoMapper;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.ArcoService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArcoServiceImpl implements ArcoService {

    private final HistorialCambioService historialCambioService;
    private final ArcoRepository arcoRepository;
    private final NodoFlujoRepository nodoFlujoRepository;
    private final PoolRepository poolRepository;
    private final ArcoMapper arcoMapper;

    @Override
    @Transactional
    public ArcoResponse crear(Long empresaId, Long usuarioId, Long origenId, Long destinoId, String etiqueta,
            String condicion, boolean porDefecto, int orden) {
        if (origenId.equals(destinoId)) {
            throw new ReglaNegocioException("Un arco no puede tener el mismo nodo como origen y destino.");
        }
        NodoFlujo origen = nodoFlujoRepository.findByIdAndEmpresaId(origenId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Nodo de origen no encontrado."));
        NodoFlujo destino = nodoFlujoRepository.findByIdAndEmpresaId(destinoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Nodo de destino no encontrado."));

        Pool poolOrigen = origen.getLane().getPool();
        Pool poolDestino = destino.getLane().getPool();
        if (!poolOrigen.getId().equals(poolDestino.getId())) {
            throw new ReglaNegocioException("El origen y el destino de un arco deben pertenecer al mismo pool.");
        }
        if (arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaId(origenId, destinoId, empresaId)) {
            throw new ReglaNegocioException("Ya existe un arco entre estos dos nodos.");
        }
        ReglasDeEventos.exigirNodosConectables(origen, destino);
        exigirCondicion(origen, condicion, porDefecto);
        exigirUnaSolaSalidaPorDefecto(empresaId, origen, condicion, porDefecto, null);

        Arco arco = arcoRepository.save(Arco.builder()
                .empresa(origen.getEmpresa())
                .origen(origen)
                .destino(destino)
                .pool(poolOrigen)
                .etiqueta(etiqueta)
                .condicion(condicion)
                .porDefecto(porDefecto)
                .orden(orden)
                .build());
        historialCambioService.registrar(empresaId, usuarioId, poolOrigen.getProceso(),
                "Flujo " + tramo(arco) + " agregado.");
        return arcoMapper.toResponse(arco);
    }

    @Override
    @Transactional
    public ArcoResponse editar(Long empresaId, Long usuarioId, Long arcoId, String etiqueta, String condicion,
            boolean porDefecto, int orden, Long version) {
        Arco arco = buscar(empresaId, arcoId);
        arco.verificarVersion(version);
        exigirCondicion(arco.getOrigen(), condicion, porDefecto);
        exigirUnaSolaSalidaPorDefecto(empresaId, arco.getOrigen(), condicion, porDefecto, arcoId);
        arco.setEtiqueta(etiqueta);
        arco.setCondicion(condicion);
        arco.setPorDefecto(porDefecto);
        arco.setOrden(orden);
        historialCambioService.registrar(empresaId, usuarioId, arco.getPool().getProceso(),
                "Flujo " + tramo(arco) + " editado.");
        return arcoMapper.toResponse(arcoRepository.saveAndFlush(arco));
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long usuarioId, Long arcoId) {
        Arco arco = buscar(empresaId, arcoId);
        arcoRepository.delete(arco);
        historialCambioService.registrar(empresaId, usuarioId, arco.getPool().getProceso(),
                "Flujo " + tramo(arco) + " eliminado.");
    }

    /**
     * R-36. En BPMN, un gateway exclusivo o inclusivo elige por condicion los arcos que salen de el, no los que
     * entran; la excepcion es la salida por defecto, que es justo la que se toma cuando ninguna condicion se cumple.
     */
    private static void exigirCondicion(NodoFlujo origen, String condicion, boolean porDefecto) {
        if (origen.exigeCondicionAlSalir() && !porDefecto && !StringUtils.hasText(condicion)) {
            throw new ReglaNegocioException(ReglasDeGateways.SIN_CONDICION);
        }
    }

    /** R-35: la salida por defecto sale de un gateway que decide, no lleva condicion y es una sola. */
    private void exigirUnaSolaSalidaPorDefecto(Long empresaId, NodoFlujo origen, String condicion, boolean porDefecto,
            Long arcoId) {
        if (!porDefecto) {
            return;
        }
        if (!origen.exigeCondicionAlSalir()) {
            throw new ReglaNegocioException(ReglasDeGateways.DEFECTO_SIN_GATEWAY);
        }
        if (StringUtils.hasText(condicion)) {
            throw new ReglaNegocioException(ReglasDeGateways.DEFECTO_CON_CONDICION);
        }
        boolean yaHayOtra = arcoRepository.findAllByOrigenIdAndEmpresaId(origen.getId(), empresaId).stream()
                .anyMatch(otro -> otro.isPorDefecto() && !otro.getId().equals(arcoId));
        if (yaHayOtra) {
            throw new ReglaNegocioException(ReglasDeGateways.DEFECTO_REPETIDO);
        }
    }

    private static String tramo(Arco arco) {
        return "de \"" + arco.getOrigen().getNombre() + "\" a \"" + arco.getDestino().getNombre() + "\"";
    }

    @Override
    public ArcoResponse obtener(Long empresaId, Long arcoId) {
        return arcoMapper.toResponse(buscar(empresaId, arcoId));
    }

    @Override
    public List<ArcoResponse> listarPorPool(Long empresaId, Long poolId) {
        if (!poolRepository.existsByIdAndEmpresaId(poolId, empresaId)) {
            throw new RecursoNoEncontradoException("Pool no encontrado.");
        }
        return arcoMapper.toResponses(arcoRepository.findAllByPoolIdAndEmpresaId(poolId, empresaId));
    }

    private Arco buscar(Long empresaId, Long arcoId) {
        return arcoRepository.findByIdAndEmpresaId(arcoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Arco no encontrado."));
    }
}
