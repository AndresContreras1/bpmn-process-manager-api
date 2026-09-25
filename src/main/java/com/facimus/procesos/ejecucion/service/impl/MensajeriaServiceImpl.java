package com.facimus.procesos.ejecucion.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajeEntranteResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajeSalienteResponse;
import com.facimus.procesos.ejecucion.mapper.MensajeriaMapper;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.MensajeEntrante;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.MensajeEntranteRepository;
import com.facimus.procesos.ejecucion.repository.MensajeSalienteRepository;
import com.facimus.procesos.ejecucion.service.DatosDelEntrante;
import com.facimus.procesos.ejecucion.service.MensajeriaService;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.gestion.service.VersionService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las dos bandejas. Lo unico que hace de verdad es recibir: guardar el mensaje que llego, averiguar a que caso
 * corresponde (D9) y, si alguien lo estaba esperando, dejar que el motor siga.
 *
 * <p>El mensaje se interpreta con la version vigente del proceso, porque es la que la tienda publica hacia fuera y
 * la que dice como se llama cada mensaje y con que clave se correlaciona. El caso, en cambio, sigue corriendo con
 * la suya: quien lo avanza es el grafo de la version con la que se abrio.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MensajeriaServiceImpl implements MensajeriaService {

    private final MensajeSalienteRepository mensajeSalienteRepository;
    private final MensajeEntranteRepository mensajeEntranteRepository;
    private final CasoRepository casoRepository;
    private final VersionService versionService;
    private final GrafosDeVersion grafos;
    private final Correlacionador correlacionador;
    private final MotorDeProcesos motor;
    private final RelojDeLaTienda reloj;
    private final Bitacora bitacora;
    private final MensajeriaMapper mensajeriaMapper;
    private final JsonMapper json;

    @Override
    @Transactional
    public MensajeEntranteResponse recibir(Long empresaId, Long procesoId, DatosDelEntrante datos) {
        Optional<MensajeEntrante> repetido = yaRecibido(empresaId, datos.claveExterna());
        if (repetido.isPresent()) {
            return mensajeriaMapper.comoRepetido(mensajeriaMapper.toEntrante(repetido.orElseThrow()));
        }
        VersionProceso version = versionService.vigente(empresaId, procesoId)
                .orElseThrow(() -> new ReglaNegocioException("El proceso no tiene una versión publicada vigente."));
        GrafoDeVersion vigente = grafos.del(version);
        MensajeDeLaVersion mensaje = vigente.mensajePorNombre(datos.nombre())
                .filter(candidato -> loRecibeLaTienda(vigente, candidato))
                .orElseThrow(() -> new ReglaNegocioException("La versión publicada del proceso no recibe ningún "
                        + "mensaje llamado \"" + datos.nombre() + "\"."));

        String clave = claveDe(datos, mensaje);
        Momento momento = Momento.en(reloj.ahora(empresaId));
        Correlacion correlacion = correlacionador.correlacionar(empresaId, procesoId, vigente, mensaje, clave);
        Caso caso = switch (correlacion.resultado()) {
            case ENTREGADO_A_CASO -> entregar(correlacion, mensaje, datos, momento);
            case CASO_NUEVO -> abrirCaso(version, vigente, correlacion, mensaje, datos, clave, momento);
            case EN_ESPERA, DESCARTADO -> correlacion.elCaso().orElse(null);
        };
        return mensajeriaMapper.toEntrante(mensajeEntranteRepository.save(MensajeEntrante.builder()
                .empresa(version.getEmpresa())
                .proceso(version.getProceso())
                .caso(caso)
                .nombre(mensaje.nombre())
                .clave(clave)
                .cuerpo(mensajeriaMapper.aJson(datos.cuerpo()))
                .origen(datos.origen())
                .claveExterna(datos.claveExterna())
                .resultado(correlacion.resultado())
                .tick(momento.tick())
                .fecha(LocalDateTime.now())
                .build()));
    }

    @Override
    public PageResponse<MensajeSalienteResponse> bandejaDeSalida(Long empresaId, Long procesoId,
            EstadoMensajeSaliente estado, Pageable pagina) {
        return PageResponse.from(mensajeSalienteRepository.bandejaDeSalida(empresaId, procesoId, estado, pagina)
                .map(mensajeriaMapper::toSaliente));
    }

    @Override
    public PageResponse<MensajeEntranteResponse> bandejaDeEntrada(Long empresaId, Long procesoId,
            ResultadoCorrelacion resultado, Pageable pagina) {
        return PageResponse.from(mensajeEntranteRepository.bandejaDeEntrada(empresaId, procesoId, resultado, pagina)
                .map(mensajeriaMapper::toEntrante));
    }

    @Override
    public List<MensajeSalienteResponse> salientesDelCaso(Long empresaId, Long casoId) {
        exigirCaso(empresaId, casoId);
        return mensajeriaMapper.toSalientes(
                mensajeSalienteRepository.findAllByCasoIdAndEmpresaIdOrderByIdAsc(casoId, empresaId));
    }

    @Override
    public List<MensajeEntranteResponse> entrantesDelCaso(Long empresaId, Long casoId) {
        exigirCaso(empresaId, casoId);
        return mensajeriaMapper.toEntrantes(
                mensajeEntranteRepository.findAllByCasoIdAndEmpresaIdOrderByIdAsc(casoId, empresaId));
    }

    /** El cuerpo entra a las variables del caso con el nombre que el mensaje declara, y el caso sigue. */
    private Caso entregar(Correlacion correlacion, MensajeDeLaVersion mensaje, DatosDelEntrante datos,
            Momento momento) {
        Caso caso = correlacion.caso();
        VariablesDelCaso variables = VariablesDelCaso.de(caso, json);
        variables.poner(mensaje.variableODelNombre(), cuerpoDe(datos));
        caso.setVariables(variables.comoJson());
        bitacora.anotar(caso, momento.tick(), TipoEventoCaso.MENSAJE_RECIBIDO, "Llego \"" + mensaje.nombre()
                + "\" y \"" + correlacion.token().getNodoNombre() + "\" deja de esperarlo.");
        motor.mensajeRecibido(caso, grafos.del(caso.getVersionProceso()), correlacion.token(), momento);
        return caso;
    }

    /** Un pedido nuevo: el mensaje abre el caso, su clave es la referencia y su cuerpo, la primera variable. */
    private Caso abrirCaso(VersionProceso version, GrafoDeVersion vigente, Correlacion correlacion,
            MensajeDeLaVersion mensaje, DatosDelEntrante datos, String clave, Momento momento) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put(mensaje.variableODelNombre(), cuerpoDe(datos));
        Caso caso = casoRepository.save(Caso.builder()
                .empresa(version.getEmpresa())
                .proceso(version.getProceso())
                .versionProceso(version)
                .referencia(clave)
                .estado(EstadoCaso.ABIERTO)
                .variables(json.writeValueAsString(variables))
                .tickInicio(momento.tick())
                .build());
        bitacora.anotar(caso, momento.tick(), TipoEventoCaso.MENSAJE_RECIBIDO,
                "Llego \"" + mensaje.nombre() + "\" y con el empieza este caso.");
        motor.arrancar(caso, vigente, correlacion.inicio(), momento);
        return caso;
    }

    /** Solo se puede recibir lo que la tienda recibe: un mensaje que ella manda no entra por aqui. */
    private static boolean loRecibeLaTienda(GrafoDeVersion vigente, MensajeDeLaVersion mensaje) {
        return mensaje.nodoDestinoId() != null && vigente.nodo(mensaje.nodoDestinoId()).isPresent();
    }

    /**
     * Con que valor busca su caso. Lo normal es que lo diga quien manda el mensaje; si no lo dice, se toma del
     * cuerpo por el campo que la correlacion declara, que es como llegan los de los socios.
     */
    private static String claveDe(DatosDelEntrante datos, MensajeDeLaVersion mensaje) {
        if (datos.clave() != null && !datos.clave().isBlank()) {
            return datos.clave();
        }
        if (mensaje.campoDeCorrelacion() == null || datos.cuerpo() == null) {
            return null;
        }
        return Optional.ofNullable(datos.cuerpo().get(mensaje.campoDeCorrelacion()))
                .map(String::valueOf)
                .orElse(null);
    }

    private static Map<String, Object> cuerpoDe(DatosDelEntrante datos) {
        return datos.cuerpo() == null ? Map.of() : datos.cuerpo();
    }

    private Optional<MensajeEntrante> yaRecibido(Long empresaId, String claveExterna) {
        return claveExterna == null || claveExterna.isBlank() ? Optional.empty()
                : mensajeEntranteRepository.findByEmpresaIdAndClaveExterna(empresaId, claveExterna);
    }

    private void exigirCaso(Long empresaId, Long casoId) {
        if (!casoRepository.existsByIdAndEmpresaId(casoId, empresaId)) {
            throw new RecursoNoEncontradoException("Caso no encontrado.");
        }
    }
}
