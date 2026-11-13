package com.facimus.procesos.ejecucion.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
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
import com.facimus.procesos.ejecucion.repository.ProcesosVisibles;
import com.facimus.procesos.ejecucion.service.TableroService;
import com.facimus.procesos.gestion.service.Actor;
import com.facimus.procesos.gestion.service.Permisos;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.Visibilidad;

import lombok.RequiredArgsConstructor;

/**
 * El tablero: diez consultas agrupadas y ninguna por caso; cuatro son del cumplimiento de los plazos (PR 71), y el de
 * un proceso pregunta antes si es de la tienda. Es el numero que importa de esta clase, porque un tablero que creciera
 * con los pedidos dejaria de poder mirarse justo el dia que hiciera falta mirarlo.
 *
 * <p>Lo de la simulacion se cuenta en ticks del reloj de la tienda y no en horas: el tiempo de la simulacion es el que
 * se puede repetir, y un tablero que mezclara los dos estaria contando dos cosas distintas en la misma columna. El
 * cumplimiento no mezcla nada: cada plazo ya dice si se cumplio, con su propio reloj.
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
    private final ProcesoService procesoService;
    private final Permisos permisos;

    @Override
    public TableroResponse de(Actor quien, Long procesoId) {
        Long empresaId = quien.empresaId();
        if (procesoId != null && !procesoService.existe(empresaId, procesoId)) {
            throw new RecursoNoEncontradoException("Proceso no encontrado.");
        }
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
                cumplimiento(empresaId, procesoId, visibles(quien)));
    }

    /**
     * PR 71: el cumplimiento de los plazos, en cuatro consultas agrupadas. El total de las tareas es la suma de lo de
     * cada proceso: cada tarea es de un proceso, asi que no hace falta preguntarlo otra vez.
     */
    private CumplimientoResponse cumplimiento(Long empresaId, Long procesoId, ProcesosVisibles visibles) {
        List<CumplimientoPorProcesoResponse> porProceso = actividadCasoRepository.cumplimientoPorProceso(empresaId,
                procesoId, visibles);
        return new CumplimientoResponse(
                porProceso.stream().mapToLong(CumplimientoPorProcesoResponse::aTiempo).sum(),
                porProceso.stream().mapToLong(CumplimientoPorProcesoResponse::tarde).sum(),
                casoRepository.cumplimientoDeCasos(empresaId, procesoId, visibles),
                porProceso,
                actividadCasoRepository.cumplimientoPorActividad(empresaId, procesoId, visibles),
                actividadCasoRepository.cumplimientoPorPersona(empresaId, procesoId, visibles));
    }

    /**
     * PR 71: lo que quien pregunta puede ver (PR 60). El cumplimiento nombra procesos y actividades, y el tablero de la
     * tienda lo lee cualquier rol: un proceso que una concesion le esconde no aparece, ni cuenta en los totales.
     */
    private ProcesosVisibles visibles(Actor quien) {
        Visibilidad visible = permisos.visibilidad(quien);
        return visible.todo() ? ProcesosVisibles.TODOS
                : new ProcesosVisibles(false, visible.carpetasOcultas(), visible.procesosOcultos(),
                        visible.procesosVisibles());
    }
}
