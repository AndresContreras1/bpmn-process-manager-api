package com.facimus.procesos.ejecucion.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.ejecucion.dto.response.CasoDetalleResponse;
import com.facimus.procesos.ejecucion.dto.response.CasoResponse;
import com.facimus.procesos.ejecucion.dto.response.EventoCasoResponse;
import com.facimus.procesos.ejecucion.mapper.CasoMapper;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.model.TipoNodoCaso;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoSpecifications;
import com.facimus.procesos.ejecucion.repository.EventoCasoRepository;
import com.facimus.procesos.ejecucion.service.CasoService;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.gestion.service.VersionService;

import lombok.RequiredArgsConstructor;

/**
 * Abrir, mirar y cerrar casos. Lo que los mueve es el motor; aqui se comprueba que se puede, se bloquea la fila
 * (D3) y se le pasa el grafo de la version.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CasoServiceImpl implements CasoService {

    private final CasoRepository casoRepository;
    private final ActividadCasoRepository actividadCasoRepository;
    private final EventoCasoRepository eventoCasoRepository;
    private final VersionService versionService;
    private final GrafosDeVersion grafos;
    private final MotorDeProcesos motor;
    private final RelojDeLaTienda reloj;
    private final Bitacora bitacora;
    private final CasoMapper casoMapper;

    /**
     * R-47 y R-48: solo se abre sobre la version vigente de un proceso propio, y solo si esa version empieza por un
     * evento de inicio. Un proceso que empieza por mensaje se abre mandando el mensaje, no desde aqui.
     */
    @Override
    @Transactional
    public CasoResponse abrir(Long empresaId, Long usuarioId, Long procesoId, String referencia,
            Map<String, Object> variables) {
        VersionProceso version = versionService.vigente(empresaId, procesoId)
                .orElseThrow(() -> new ReglaNegocioException("El proceso no tiene una versión publicada vigente."));
        GrafoDeVersion grafo = grafos.del(empresaId, version);
        NodoDeLaVersion inicio = grafo.inicioAMano().orElseThrow(() -> new ReglaNegocioException(porDondeEmpieza(
                grafo)));

        Momento momento = new Momento(reloj.ahora(empresaId), usuarioId);
        Caso caso = casoRepository.save(Caso.builder()
                .empresa(version.getEmpresa())
                .proceso(version.getProceso())
                .versionProceso(version)
                .referencia(referencia)
                .estado(EstadoCaso.ABIERTO)
                .variables(casoMapper.aJson(variables))
                .tickInicio(momento.tick())
                .build());
        motor.arrancar(caso, grafo, inicio, momento);
        return casoMapper.toResponse(caso);
    }

    /** Por que no se puede abrir a mano: porque lo abre un mensaje, o porque la version no empieza en ningun lado. */
    private static String porDondeEmpieza(GrafoDeVersion grafo) {
        return grafo.inicioPorMensaje()
                .map(inicio -> "Este proceso se inicia con el mensaje \""
                        + grafo.mensajeQueEspera(inicio.id()).map(MensajeDeLaVersion::nombre)
                                .orElse(inicio.nombre())
                        + "\"; envíelo como mensaje entrante.")
                .orElse("La versión publicada no tiene ningún evento de inicio.");
    }

    @Override
    public PageResponse<CasoResponse> listar(Long empresaId, Long procesoId, EstadoCaso estado, String referencia,
            Pageable pagina) {
        return PageResponse.from(casoRepository
                .findAll(CasoSpecifications.conFiltros(empresaId, procesoId, estado, referencia), pagina)
                .map(casoMapper::toResponse));
    }

    @Override
    public CasoDetalleResponse obtener(Long empresaId, Long casoId) {
        Caso caso = buscar(empresaId, casoId);
        List<ActividadCaso> pasos = actividadCasoRepository.findAllByCasoIdAndEmpresaIdOrderByIdAsc(casoId,
                empresaId);
        return new CasoDetalleResponse(casoMapper.toResponse(caso), casoMapper.toPasos(pasos),
                casoMapper.aMapa(caso.getVariables()));
    }

    @Override
    public List<EventoCasoResponse> eventos(Long empresaId, Long casoId) {
        buscar(empresaId, casoId);
        return casoMapper.toEventos(eventoCasoRepository.findAllByCasoIdAndEmpresaIdOrderByIdAsc(casoId, empresaId));
    }

    @Override
    @Transactional
    public CasoResponse cancelar(Long empresaId, Long usuarioId, Long casoId) {
        Caso caso = bloquear(empresaId, casoId);
        if (caso.getEstado().estaCerrado()) {
            throw new ReglaNegocioException("El caso ya está cerrado.");
        }
        Momento momento = new Momento(reloj.ahora(empresaId), usuarioId);
        motor.apagarTokens(caso, momento);
        cerrar(caso, EstadoCaso.CANCELADO, momento);
        bitacora.anotar(caso, momento.tick(), TipoEventoCaso.CASO_CANCELADO,
                "El caso se cancelo antes de terminar.", usuarioId);
        return casoMapper.toResponse(caso);
    }

    @Override
    @Transactional
    public CasoResponse corregirVariables(Long empresaId, Long casoId, Map<String, Object> variables, Long version) {
        Caso caso = bloquear(empresaId, casoId);
        caso.verificarVersion(version);
        if (caso.getEstado().estaCerrado()) {
            throw new ReglaNegocioException("El caso ya está cerrado.");
        }
        caso.setVariables(casoMapper.aJson(variables));
        return casoMapper.toResponse(casoRepository.save(caso));
    }

    /**
     * Vuelve a poner pendientes los gateways que se quedaron esperando y deja que el motor los evalue otra vez con
     * las variables de ahora. Un join que todavia tenga que esperar volvera a quedarse esperando, sin dano.
     */
    @Override
    @Transactional
    public CasoResponse reintentar(Long empresaId, Long usuarioId, Long casoId) {
        Caso caso = bloquear(empresaId, casoId);
        if (caso.getEstado() != EstadoCaso.ERROR) {
            throw new ReglaNegocioException("El caso no está en error: no hay nada que reintentar.");
        }
        actividadCasoRepository.findAllByCasoIdAndEmpresaIdAndEstadoOrderByIdAsc(casoId, empresaId,
                        EstadoActividadCaso.EN_ESPERA).stream()
                .filter(paso -> paso.getTipoNodo() == TipoNodoCaso.GATEWAY)
                .forEach(paso -> {
                    paso.setEstado(EstadoActividadCaso.PENDIENTE);
                    actividadCasoRepository.save(paso);
                });
        caso.setEstado(EstadoCaso.ABIERTO);
        motor.avanzar(caso, grafos.del(empresaId, caso.getVersionProceso()),
                new Momento(reloj.ahora(empresaId), usuarioId));
        return casoMapper.toResponse(caso);
    }

    private void cerrar(Caso caso, EstadoCaso estado, Momento momento) {
        caso.setEstado(estado);
        caso.setTickFin(momento.tick());
        caso.setFechaFin(LocalDateTime.now());
        casoRepository.save(caso);
    }

    private Caso buscar(Long empresaId, Long casoId) {
        return casoRepository.findByIdAndEmpresaId(casoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Caso no encontrado."));
    }

    /** D3: quien va a mover el caso lo bloquea primero, y lo suelta al terminar la transaccion. */
    private Caso bloquear(Long empresaId, Long casoId) {
        return casoRepository.bloquear(casoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Caso no encontrado."));
    }
}
