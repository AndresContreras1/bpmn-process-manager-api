package com.facimus.procesos.ejecucion.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.ejecucion.dto.response.CasosPorEstadoResponse;
import com.facimus.procesos.ejecucion.dto.response.CumplimientoPorProcesoResponse;
import com.facimus.procesos.ejecucion.dto.response.CumplimientoResponse;
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
 * El tablero: diez consultas agrupadas y ninguna por caso (PR 71: cuatro son del cumplimiento de los plazos). Es el numero que importa de esta clase, porque un
 * tablero que creciera con los pedidos dejaria de poder mirarse justo el dia que hiciera falta mirarlo.
 *
 * <p>Todo se cuenta en ticks del reloj de la tienda y no en horas: el tiempo de la simulacion es el que se puede
 * repetir, y un tablero que mezclara los dos estaria contando dos cosas distintas en la misma columna.
 *
 * <p>PR 68: los casos de una prueba del borrador no cuentan. Se listan y sus tareas llegan a las bandejas, para que
 * quien prueba recorra cada camino, pero no son pedidos: cada consulta del tablero pide los de una version publicada.
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
                TiempoDeCiclo.de(casoRepository.ticksDeCiclo(empresaId, procesoId)),
                actividadCasoRepository.tareasPorRol(empresaId, procesoId),
                mensajeSalienteRepository.salientesPorEstado(empresaId, procesoId),
                mensajeEntranteRepository.entrantesPorResultado(empresaId, procesoId),
                new LoQueSalioMalResponse(salioMal.getOrDefault(TipoEventoCaso.ENVIO_FALLIDO, 0L),
                        salioMal.getOrDefault(TipoEventoCaso.SIN_CAMINO, 0L),
                        salioMal.getOrDefault(TipoEventoCaso.VARIABLE_AUSENTE, 0L)),
                cumplimiento(empresaId, procesoId));
    }

    /**
     * PR 71: el cumplimiento de los plazos, en cuatro consultas agrupadas. El total de las tareas es la suma de lo de
     * cada proceso: cada tarea es de un proceso, asi que no hace falta preguntarlo otra vez.
     */
    private CumplimientoResponse cumplimiento(Long empresaId, Long procesoId) {
        List<CumplimientoPorProcesoResponse> porProceso = actividadCasoRepository.cumplimientoPorProceso(empresaId,
                procesoId);
        return new CumplimientoResponse(
                porProceso.stream().mapToLong(CumplimientoPorProcesoResponse::aTiempo).sum(),
                porProceso.stream().mapToLong(CumplimientoPorProcesoResponse::tarde).sum(),
                casoRepository.cumplimientoDeCasos(empresaId, procesoId),
                porProceso,
                actividadCasoRepository.cumplimientoPorActividad(empresaId, procesoId),
                actividadCasoRepository.cumplimientoPorPersona(empresaId, procesoId));
    }

}
