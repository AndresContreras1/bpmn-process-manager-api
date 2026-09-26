package com.facimus.procesos.ejecucion.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.ejecucion.dto.response.MensajeEntranteResponse;
import com.facimus.procesos.ejecucion.dto.response.PanelDeSimulacionResponse;
import com.facimus.procesos.ejecucion.dto.response.PedidosSimuladosResponse;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.OrigenMensajeEntrante;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;
import com.facimus.procesos.ejecucion.puerto.GeneradorDePedidos;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.MensajeEntranteRepository;
import com.facimus.procesos.ejecucion.repository.MensajeSalienteRepository;
import com.facimus.procesos.ejecucion.service.DatosDelEntrante;
import com.facimus.procesos.ejecucion.service.MensajeriaService;
import com.facimus.procesos.ejecucion.service.SimulacionService;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.VersionService;

import lombok.RequiredArgsConstructor;

/**
 * D8: mover el reloj. Un tick no es tiempo real, es un paso: la tienda avanza tantos pasos y lo que con ese paso ya
 * tendria que haber llegado, llega. Asi una prueba, o una demo, dice cuando pasan las cosas.
 *
 * <p>El tick no abre transaccion. Cada mensaje se entrega en la suya, con su caso bloqueado (D3): veinte pedidos
 * se mueven uno detras de otro, y si uno se tuerce los demas no se quedan a medias.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SimulacionServiceImpl implements SimulacionService {

    /** Cuantos ticks se pueden mover de una vez. Mas que esto no es una demo, es una carga, y esa tiene su k6. */
    private static final int TICKS_MAXIMOS = 100;
    /** Cuantos pedidos se pueden pedir de una vez, por lo mismo. */
    private static final int PEDIDOS_MAXIMOS = 200;

    private final MensajeSalienteRepository mensajeSalienteRepository;
    private final MensajeEntranteRepository mensajeEntranteRepository;
    private final CasoRepository casoRepository;
    private final ConfiguracionTiendaService configuracionTiendaService;
    private final VersionService versionService;
    private final GrafosDeVersion grafos;
    private final MensajeriaService mensajeriaService;
    private final GeneradorDePedidos generadorDePedidos;
    private final ParametrosDeLaTienda parametros;
    private final EntregaDeMensajes entrega;
    private final RelojDeLaTienda reloj;

    @Override
    public PanelDeSimulacionResponse panel(Long empresaId) {
        return contarLoQueHay(empresaId);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PanelDeSimulacionResponse tick(Long empresaId, int ticks) {
        if (ticks < 1 || ticks > TICKS_MAXIMOS) {
            throw new ReglaNegocioException("El reloj se mueve entre 1 y " + TICKS_MAXIMOS + " ticks por vez.");
        }
        int ahora = reloj.avanzar(empresaId, ticks);
        // Primero lo que ya habia llegado y nadie esperaba, porque llego antes: si ahora si lo esperan, le toca a
        // ese y no a la respuesta que venga en este mismo tick.
        for (Long entrante : entrega.pendientes(empresaId, ahora)) {
            entrega.reintentar(empresaId, entrante);
        }
        for (Long saliente : entrega.vencidos(empresaId, ahora)) {
            entrega.entregar(empresaId, saliente, ahora);
        }
        return contarLoQueHay(empresaId);
    }

    /**
     * El panel sin promesa de transaccion, que es lo que lo hace servible desde los dos sitios: el endpoint lo
     * pide dentro de la suya, y el tick, que corre sin ninguna a proposito, lo pide al terminar.
     *
     * <p>Esta separado porque una llamada de la clase a si misma no pasa por el proxy de Spring: si el tick
     * llamara al metodo publico, la anotacion de ese metodo no se aplicaria y estaria prometiendo una
     * transaccion que nadie abre.
     */
    private PanelDeSimulacionResponse contarLoQueHay(Long empresaId) {
        var configuracion = configuracionTiendaService.obtener(empresaId);
        return new PanelDeSimulacionResponse(configuracion.reloj(), configuracion.modoSimulacion(),
                mensajeSalienteRepository.countByEmpresaIdAndEstado(empresaId, EstadoMensajeSaliente.PENDIENTE),
                mensajeSalienteRepository.pendientesPorSocio(empresaId),
                mensajeEntranteRepository.countByEmpresaIdAndResultado(empresaId, ResultadoCorrelacion.EN_ESPERA));
    }

    /**
     * Cada pedido entra como el mensaje que el proceso dice que lo abre, con su referencia numerada a partir de
     * los que ya tiene. Se mandan uno a uno, cada uno en su transaccion, por lo mismo que los ticks.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PedidosSimuladosResponse pedidos(Long empresaId, Long procesoId, int cantidad,
            Map<String, Object> plantilla) {
        if (cantidad < 1 || cantidad > PEDIDOS_MAXIMOS) {
            throw new ReglaNegocioException("Se piden entre 1 y " + PEDIDOS_MAXIMOS + " pedidos por vez.");
        }
        String mensaje = mensajeQueAbreElProceso(empresaId, procesoId);
        List<String> referencias = referencias(empresaId, procesoId, cantidad);
        List<Map<String, Object>> cuerpos = generadorDePedidos.pedidos(cantidad, plantilla, referencias,
                parametros.de(empresaId));

        List<String> abiertos = new ArrayList<>();
        for (int numero = 0; numero < cantidad; numero++) {
            MensajeEntranteResponse entrante = mensajeriaService.recibir(empresaId, procesoId,
                    new DatosDelEntrante(mensaje, referencias.get(numero), cuerpos.get(numero),
                            "SIM-" + referencias.get(numero), OrigenMensajeEntrante.CLIENTE_SIMULADO));
            if (entrante.resultado() == ResultadoCorrelacion.CASO_NUEVO) {
                abiertos.add(referencias.get(numero));
            }
        }
        return new PedidosSimuladosResponse(mensaje, cantidad, abiertos.size(), List.copyOf(abiertos));
    }

    /**
     * Con que mensaje se abre un caso de este proceso. Un proceso que empieza a mano no tiene ninguno, y pedirle
     * pedidos simulados no tiene sentido: los abre quien quiera, uno por uno.
     */
    private String mensajeQueAbreElProceso(Long empresaId, Long procesoId) {
        GrafoDeVersion vigente = grafos.del(empresaId, versionService.vigente(empresaId, procesoId)
                .orElseThrow(() -> new ReglaNegocioException("El proceso no tiene una versión publicada "
                        + "vigente.")));
        return vigente.inicioPorMensaje()
                .flatMap(inicio -> vigente.mensajeQueEspera(inicio.id()))
                .map(MensajeDeLaVersion::nombre)
                .orElseThrow(() -> new ReglaNegocioException("Este proceso no se inicia con un mensaje: sus casos "
                        + "se abren uno a uno."));
    }

    /** Numeradas a partir de los casos que el proceso ya tiene, para que dos tandas no choquen entre si. */
    private List<String> referencias(Long empresaId, Long procesoId, int cantidad) {
        long desde = casoRepository.countByEmpresaIdAndProcesoId(empresaId, procesoId);
        return IntStream.rangeClosed(1, cantidad)
                .mapToObj(numero -> "SIM-" + procesoId + "-" + (desde + numero))
                .toList();
    }
}
