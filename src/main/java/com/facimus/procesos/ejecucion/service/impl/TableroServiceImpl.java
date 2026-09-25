package com.facimus.procesos.ejecucion.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.ejecucion.dto.response.CasosPorEstadoResponse;
import com.facimus.procesos.ejecucion.dto.response.CicloDeCasoResponse;
import com.facimus.procesos.ejecucion.dto.response.LoQueSalioMalResponse;
import com.facimus.procesos.ejecucion.dto.response.TableroResponse;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.EventoCasoRepository;
import com.facimus.procesos.ejecucion.repository.MensajeEntranteRepository;
import com.facimus.procesos.ejecucion.repository.MensajeSalienteRepository;
import com.facimus.procesos.ejecucion.service.TableroService;

import lombok.RequiredArgsConstructor;

/**
 * El tablero: seis consultas agrupadas y ninguna por caso. Es el numero que importa de esta clase, porque un
 * tablero que creciera con los pedidos dejaria de poder mirarse justo el dia que hiciera falta mirarlo.
 *
 * <p>Todo se cuenta en ticks del reloj de la tienda y no en horas: el tiempo de la simulacion es el que se puede
 * repetir, y un tablero que mezclara los dos estaria contando dos cosas distintas en la misma columna.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TableroServiceImpl implements TableroService {

    /** Las tres que dejan un pedido parado o torcido; cada una se arregla de una forma distinta. */
    private static final List<TipoEventoCaso> LO_QUE_SALE_MAL = List.of(TipoEventoCaso.ENVIO_FALLIDO,
            TipoEventoCaso.SIN_CAMINO, TipoEventoCaso.VARIABLE_AUSENTE);

    private final CasoRepository casoRepository;
    private final ActividadCasoRepository actividadCasoRepository;
    private final MensajeSalienteRepository mensajeSalienteRepository;
    private final MensajeEntranteRepository mensajeEntranteRepository;
    private final EventoCasoRepository eventoCasoRepository;

    @Override
    public TableroResponse de(Long empresaId, Long procesoId) {
        List<CasosPorEstadoResponse> porEstado = casoRepository.casosPorEstado(empresaId, procesoId);
        Map<TipoEventoCaso, Long> salioMal = eventoCasoRepository
                .contarPorTipo(empresaId, procesoId, LO_QUE_SALE_MAL).stream()
                .collect(Collectors.toMap(fila -> (TipoEventoCaso) fila[0], fila -> (Long) fila[1]));

        return new TableroResponse(procesoId,
                porEstado.stream().mapToLong(CasosPorEstadoResponse::cantidad).sum(),
                porEstado,
                ciclo(casoRepository.ticksDeCiclo(empresaId, procesoId)),
                actividadCasoRepository.tareasPorRol(empresaId, procesoId),
                mensajeSalienteRepository.salientesPorEstado(empresaId, procesoId),
                mensajeEntranteRepository.entrantesPorResultado(empresaId, procesoId),
                new LoQueSalioMalResponse(salioMal.getOrDefault(TipoEventoCaso.ENVIO_FALLIDO, 0L),
                        salioMal.getOrDefault(TipoEventoCaso.SIN_CAMINO, 0L),
                        salioMal.getOrDefault(TipoEventoCaso.VARIABLE_AUSENTE, 0L)));
    }

    /**
     * El promedio y el p95 de una lista que ya viene ordenada. Se calculan aqui y no en SQL porque el percentil no
     * se escribe igual en H2 que en PostgreSQL, y un numero que se publica no puede salir distinto segun el motor.
     */
    private static CicloDeCasoResponse ciclo(List<Integer> ticks) {
        if (ticks.isEmpty()) {
            return CicloDeCasoResponse.sinDatos();
        }
        double medio = ticks.stream().mapToInt(Integer::intValue).average().orElseThrow();
        // El menor valor que cubre al menos el 95 % de los pedidos: con veinte, el ultimo; con diez, el decimo.
        int posicion = (int) Math.ceil(ticks.size() * 0.95) - 1;
        return new CicloDeCasoResponse(ticks.size(), Math.round(medio * 100) / 100.0, ticks.get(posicion));
    }
}
