package com.facimus.procesos.modelado.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.event.ProcesoModificado;
import com.facimus.procesos.modelado.model.AristaBpmn;
import com.facimus.procesos.modelado.model.DisposicionBpmn;
import com.facimus.procesos.modelado.model.DisposicionBpmn.ArcoPlano;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Arista;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Forma;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Geometria;
import com.facimus.procesos.modelado.model.DisposicionBpmn.LanePlana;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Limites;
import com.facimus.procesos.modelado.model.DisposicionBpmn.MensajePlano;
import com.facimus.procesos.modelado.model.DisposicionBpmn.NodoPlano;
import com.facimus.procesos.modelado.model.DisposicionBpmn.PoolPlano;
import com.facimus.procesos.modelado.model.FormaBpmn;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.AristaBpmnRepository;
import com.facimus.procesos.modelado.repository.EventoRepository;
import com.facimus.procesos.modelado.repository.FormaBpmnRepository;
import com.facimus.procesos.modelado.repository.GatewayRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.GeometriaService;

import lombok.RequiredArgsConstructor;

/**
 * PR 52: lee el modelo del proceso, lo pasa por {@link DisposicionBpmn} y reemplaza las formas y aristas guardadas.
 * Corre dentro de la transaccion del cambio, con lo que el cambio ya hizo: si algo falla, el cambio tampoco queda.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GeometriaServiceImpl implements GeometriaService {

    private final PoolRepository poolRepository;
    private final LaneRepository laneRepository;
    private final ActividadRepository actividadRepository;
    private final GatewayRepository gatewayRepository;
    private final EventoRepository eventoRepository;
    private final ArcoRepository arcoRepository;
    private final MensajeRepository mensajeRepository;
    private final FormaBpmnRepository formaRepository;
    private final AristaBpmnRepository aristaRepository;

    /** Cada cambio de un proceso pasa por su historial, y el historial lo avisa. */
    @EventListener
    @Transactional
    public void alModificarse(ProcesoModificado evento) {
        reemplazar(evento.empresaId(), evento.procesoId());
    }

    @Override
    @Transactional
    public void recalcular(Long empresaId, Long procesoId) {
        reemplazar(empresaId, procesoId);
    }

    private void reemplazar(Long empresaId, Long procesoId) {
        List<Pool> pools = poolRepository.findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(procesoId, empresaId);
        formaRepository.borrarDelProceso(procesoId, empresaId);
        aristaRepository.borrarDelProceso(procesoId, empresaId);
        if (pools.isEmpty()) {
            return;
        }
        Empresa empresa = pools.get(0).getEmpresa();
        List<NodoFlujo> nodos = Stream.of(
                        actividadRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId),
                        gatewayRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId),
                        eventoRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId))
                .flatMap(List::stream)
                .map(NodoFlujo.class::cast)
                .toList();
        Geometria geometria = DisposicionBpmn.calcular(
                pools.stream().map(pool -> new PoolPlano(pool.getId(), pool.getBpmnId(),
                        pool.getTipoParticipante().name(), pool.getOrden(), pool.isCajaNegra())).toList(),
                laneRepository.delProcesoEnOrden(procesoId, empresaId).stream()
                        .map(lane -> new LanePlana(lane.getId(), lane.getBpmnId(), lane.getPool().getId(),
                                lane.getOrden()))
                        .toList(),
                nodos.stream().map(nodo -> new NodoPlano(nodo.getId(), nodo.getBpmnId(), nodo.getLane().getId(),
                        claseDe(nodo), nodo.getPosicionX(), nodo.getPosicionY(), nodo.getNombre())).toList(),
                arcoRepository.findAllByPool_ProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId).stream()
                        .map(arco -> new ArcoPlano(arco.getBpmnId(), arco.getOrigen().getId(),
                                arco.getDestino().getId(), arco.getEtiqueta()))
                        .toList(),
                mensajeRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId).stream()
                        .map(mensaje -> new MensajePlano(mensaje.getId(), mensaje.getBpmnId(),
                                mensaje.getPoolOrigen().getId(), mensaje.getPoolDestino().getId(),
                                mensaje.getNodoOrigen() == null ? null : mensaje.getNodoOrigen().getId(),
                                mensaje.getNodoDestino() == null ? null : mensaje.getNodoDestino().getId()))
                        .toList());
        formaRepository.saveAll(geometria.formas().stream().map(forma -> forma(empresa, procesoId, forma)).toList());
        aristaRepository.saveAll(geometria.aristas().stream().map(arista -> arista(empresa, procesoId, arista))
                .toList());
    }

    @Override
    public Geometria obtener(Long empresaId, Long procesoId) {
        List<Forma> formas = new ArrayList<>();
        for (FormaBpmn forma : formaRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId)) {
            formas.add(new Forma(forma.getElemento(), forma.getTipo(),
                    new Limites(forma.getX(), forma.getY(), forma.getAncho(), forma.getAlto()),
                    limites(forma.getEtiquetaX(), forma.getEtiquetaY(), forma.getEtiquetaAncho(),
                            forma.getEtiquetaAlto())));
        }
        List<Arista> aristas = new ArrayList<>();
        for (AristaBpmn arista : aristaRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId)) {
            aristas.add(new Arista(arista.getElemento(), arista.getTipo(), arista.leerPuntos(),
                    limites(arista.getEtiquetaX(), arista.getEtiquetaY(), arista.getEtiquetaAncho(),
                            arista.getEtiquetaAlto())));
        }
        return new Geometria(formas, aristas);
    }

    static String claseDe(NodoFlujo nodo) {
        return switch (nodo.prefijoBpmn()) {
            case "Gateway" -> "COMPUERTA";
            case "Event" -> "EVENTO";
            default -> "TAREA";
        };
    }

    private static FormaBpmn forma(Empresa empresa, Long procesoId, Forma forma) {
        Limites etiqueta = forma.etiqueta();
        return FormaBpmn.builder()
                .empresa(empresa)
                .procesoId(procesoId)
                .elemento(forma.elemento())
                .tipo(forma.tipo())
                .x(forma.limites().x())
                .y(forma.limites().y())
                .ancho(forma.limites().ancho())
                .alto(forma.limites().alto())
                .etiquetaX(etiqueta == null ? null : etiqueta.x())
                .etiquetaY(etiqueta == null ? null : etiqueta.y())
                .etiquetaAncho(etiqueta == null ? null : etiqueta.ancho())
                .etiquetaAlto(etiqueta == null ? null : etiqueta.alto())
                .build();
    }

    private static AristaBpmn arista(Empresa empresa, Long procesoId, Arista arista) {
        Limites etiqueta = arista.etiqueta();
        return AristaBpmn.builder()
                .empresa(empresa)
                .procesoId(procesoId)
                .elemento(arista.elemento())
                .tipo(arista.tipo())
                .puntos(AristaBpmn.escribir(arista.puntos()))
                .etiquetaX(etiqueta == null ? null : etiqueta.x())
                .etiquetaY(etiqueta == null ? null : etiqueta.y())
                .etiquetaAncho(etiqueta == null ? null : etiqueta.ancho())
                .etiquetaAlto(etiqueta == null ? null : etiqueta.alto())
                .build();
    }

    private static Limites limites(Double x, Double y, Double ancho, Double alto) {
        return x == null ? null : new Limites(x, y, ancho, alto);
    }
}
