package com.facimus.procesos.ejecucion.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.MensajeEntrante;
import com.facimus.procesos.ejecucion.model.MensajeSaliente;
import com.facimus.procesos.ejecucion.model.OrigenMensajeEntrante;
import com.facimus.procesos.ejecucion.puerto.MensajeParaElSocio;
import com.facimus.procesos.ejecucion.puerto.RespuestaDelSocio;
import com.facimus.procesos.ejecucion.puerto.RespuestaEntrante;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.MensajeEntranteRepository;
import com.facimus.procesos.ejecucion.repository.MensajeSalienteRepository;
import com.facimus.procesos.ejecucion.service.DatosDelEntrante;
import com.facimus.procesos.ejecucion.service.MensajeriaService;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lo que pasa cuando el reloj llega al tick de entrega de un mensaje: se lo damos al socio que atiende a ese
 * participante, se anota si llego, y lo que conteste vuelve a entrar como un mensaje mas.
 *
 * <p>Cada mensaje se entrega en su propia transaccion, con el caso bloqueado (D3). Un tick que mueve veinte
 * pedidos no los mueve a la vez: los mueve uno detras de otro, y si uno falla los demas no se quedan a medias.
 */
@Component
@RequiredArgsConstructor
class EntregaDeMensajes {

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {
    };

    private final MensajeSalienteRepository mensajeSalienteRepository;
    private final MensajeEntranteRepository mensajeEntranteRepository;
    private final CasoRepository casoRepository;
    private final GrafosDeVersion grafos;
    private final SociosSimulados socios;
    private final ParametrosDeLaTienda parametros;
    private final MensajeriaService mensajeriaService;
    private final MotorDeProcesos motor;
    private final JsonMapper json;

    /** Los mensajes que a este tick ya tendrian que haber llegado, en orden de vencimiento. */
    @Transactional(readOnly = true)
    List<Long> vencidos(Long empresaId, int tick) {
        return mensajeSalienteRepository.vencidos(empresaId, tick).stream().map(MensajeSaliente::getId).toList();
    }

    /**
     * Entrega un mensaje. Se vuelve a mirar su estado con el caso ya bloqueado: entre que el tick lo eligio y
     * llegamos aqui, otro hilo pudo haberlo entregado, y entregarlo dos veces seria contestarle dos veces al caso.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void entregar(Long empresaId, Long salienteId, int tick) {
        Optional<Long> casoId = mensajeSalienteRepository.findByIdAndEmpresaId(salienteId, empresaId)
                .map(saliente -> saliente.getCaso().getId());
        if (casoId.isEmpty()) {
            return;
        }
        Caso caso = casoRepository.bloquear(casoId.orElseThrow(), empresaId).orElseThrow();
        MensajeSaliente saliente = mensajeSalienteRepository.findByIdAndEmpresaId(salienteId, empresaId)
                .orElseThrow();
        if (!saliente.getEstado().estaPendiente()) {
            return;
        }
        GrafoDeVersion grafo = grafos.del(caso.getVersionProceso());
        Optional<MensajeDeLaVersion> definicion = grafo.mensajePorNombre(saliente.getNombre());
        RespuestaDelSocio respuesta = socios.paraA(saliente.getIntegracion())
                .recibir(new MensajeParaElSocio(caso.getId(), saliente.getNombre(), saliente.getClave(),
                        json.readValue(saliente.getCuerpo(), MAPA), respuestaEsperada(definicion), tick,
                        parametros.de(empresaId)));

        saliente.setIntentos(saliente.getIntentos() + 1);
        saliente.setEstado(respuesta.entregado() ? EstadoMensajeSaliente.ENTREGADO : EstadoMensajeSaliente.FALLIDO);
        saliente.setError(respuesta.error());
        mensajeSalienteRepository.save(saliente);

        if (!respuesta.entregado()) {
            definicion.ifPresent(mensaje ->
                    motor.envioFallido(caso, grafo, mensaje, respuesta.error(), Momento.en(tick)));
            return;
        }
        for (RespuestaEntrante contestacion : respuesta.respuestas()) {
            mensajeriaService.recibir(empresaId, caso.getProceso().getId(),
                    new DatosDelEntrante(contestacion.nombre(), contestacion.clave(), contestacion.cuerpo(), null,
                            OrigenMensajeEntrante.delSocio(saliente.getIntegracion())));
        }
    }

    /**
     * Vuelve a mirar los mensajes que llegaron antes de que nadie los esperara. Es aqui y no al recibirlos porque
     * lo que puede haber cambiado desde entonces es el caso: ahora si esta parado esperandolos.
     */
    @Transactional(readOnly = true)
    List<Long> enEspera(Long empresaId) {
        return mensajeEntranteRepository.enEsperaDeLaTienda(empresaId).stream()
                .map(MensajeEntrante::getId).toList();
    }

    /** Reintenta uno, en su propia transaccion: el tick mueve muchos y ninguno se lleva a los demas por delante. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void reintentar(Long empresaId, Long entranteId) {
        mensajeriaService.reintentar(empresaId, entranteId);
    }

    private static String respuestaEsperada(Optional<MensajeDeLaVersion> definicion) {
        return definicion.map(MensajeDeLaVersion::respuestaEsperada).orElse(null);
    }
}
