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
import com.facimus.procesos.modelado.service.DatosDeArco;

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
    public ArcoResponse crear(Long empresaId, Long usuarioId, DatosDeArco datos) {
        exigirNodosDistintos(datos.origenId(), datos.destinoId());
        NodoFlujo origen = nodo(empresaId, datos.origenId(), "Nodo de origen no encontrado.");
        NodoFlujo destino = nodo(empresaId, datos.destinoId(), "Nodo de destino no encontrado.");
        Pool pool = exigirUnTramoValido(empresaId, origen, destino, datos, null);

        Arco arco = arcoRepository.save(Arco.builder()
                .empresa(origen.getEmpresa())
                .origen(origen)
                .destino(destino)
                .pool(pool)
                .etiqueta(datos.etiqueta())
                .condicion(datos.condicion())
                .porDefecto(datos.porDefecto())
                .orden(datos.orden())
                .build());
        historialCambioService.registrar(empresaId, usuarioId, pool.getProceso(),
                "Flujo " + tramo(arco) + " agregado.");
        return arcoMapper.toResponse(arco);
    }

    /**
     * R-42: cambiar un extremo del arco vuelve a pasar por las mismas reglas que crearlo, porque lo que queda es
     * otro tramo del diagrama. Un extremo que no llega deja el que ya tenia: mover la flecha y renombrarla son dos
     * gestos distintos del editor.
     */
    @Override
    @Transactional
    public ArcoResponse editar(Long empresaId, Long usuarioId, Long arcoId, DatosDeArco datos, Long version) {
        Arco arco = buscar(empresaId, arcoId);
        arco.verificarVersion(version);
        // Un extremo que no llega deja el que ya tenia, asi que la comparacion es entre los dos que quedarian.
        Long origenId = datos.origenId() == null ? arco.getOrigen().getId() : datos.origenId();
        Long destinoId = datos.destinoId() == null ? arco.getDestino().getId() : datos.destinoId();
        exigirNodosDistintos(origenId, destinoId);
        NodoFlujo origen = origenId.equals(arco.getOrigen().getId())
                ? arco.getOrigen()
                : nodo(empresaId, origenId, "Nodo de origen no encontrado.");
        NodoFlujo destino = destinoId.equals(arco.getDestino().getId())
                ? arco.getDestino()
                : nodo(empresaId, destinoId, "Nodo de destino no encontrado.");
        Pool pool = exigirUnTramoValido(empresaId, origen, destino, datos, arcoId);

        String antes = tramo(arco);
        arco.setOrigen(origen);
        arco.setDestino(destino);
        arco.setPool(pool);
        arco.setEtiqueta(datos.etiqueta());
        arco.setCondicion(datos.condicion());
        arco.setPorDefecto(datos.porDefecto());
        arco.setOrden(datos.orden());
        String despues = tramo(arco);
        historialCambioService.registrar(empresaId, usuarioId, pool.getProceso(), antes.equals(despues)
                ? "Flujo " + despues + " editado."
                : "Flujo " + antes + " movido a " + despues + ".");
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

    /** Las reglas del tramo: si va a alguna parte, si ya existe, y que exige el nodo del que sale. */
    private Pool exigirUnTramoValido(Long empresaId, NodoFlujo origen, NodoFlujo destino, DatosDeArco datos,
            Long arcoId) {
        Pool poolOrigen = origen.getLane().getPool();
        if (!poolOrigen.getId().equals(destino.getLane().getPool().getId())) {
            throw new ReglaNegocioException("El origen y el destino de un arco deben pertenecer al mismo pool.");
        }
        if (yaExiste(empresaId, origen, destino, arcoId)) {
            throw new ReglaNegocioException("Ya existe un arco entre estos dos nodos.");
        }
        ReglasDeEventos.exigirNodosConectables(origen, destino);
        exigirCondicion(origen, datos.condicion(), datos.porDefecto());
        exigirUnaSolaSalidaPorDefecto(empresaId, origen, datos.condicion(), datos.porDefecto(), arcoId);
        return poolOrigen;
    }

    private static void exigirNodosDistintos(Long origenId, Long destinoId) {
        if (origenId.equals(destinoId)) {
            throw new ReglaNegocioException("Un arco no puede tener el mismo nodo como origen y destino.");
        }
    }

    private boolean yaExiste(Long empresaId, NodoFlujo origen, NodoFlujo destino, Long arcoId) {
        return arcoId == null
                ? arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaId(origen.getId(), destino.getId(), empresaId)
                : arcoRepository.existsByOrigenIdAndDestinoIdAndEmpresaIdAndIdNot(origen.getId(), destino.getId(),
                        empresaId, arcoId);
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

    private NodoFlujo nodo(Long empresaId, Long nodoId, String siNoEsta) {
        return nodoFlujoRepository.findByIdAndEmpresaId(nodoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(siNoEsta));
    }

    private Arco buscar(Long empresaId, Long arcoId) {
        return arcoRepository.findByIdAndEmpresaId(arcoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Arco no encontrado."));
    }
}
