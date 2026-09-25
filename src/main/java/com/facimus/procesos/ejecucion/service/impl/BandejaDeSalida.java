package com.facimus.procesos.ejecucion.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.MensajeSaliente;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.repository.MensajeSalienteRepository;
import com.facimus.procesos.modelado.model.CampoDeMensaje;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mandar un mensaje es escribirlo en la bandeja de salida: una fila que dice a quien va, que lleva dentro y en que
 * tick le toca llegar. Nadie llama a nadie; quien lo entrega es el reloj (D9).
 *
 * <p>Un mensaje no llega en el mismo tick en que sale. Si llegara, mover el reloj un tick entregaria de golpe todo
 * lo que el caso acaba de mandar y la simulacion no tendria forma: no se podria ver un pedido esperando la
 * respuesta de la pasarela, que es justo lo que hay que poder ver.
 */
@Component
@RequiredArgsConstructor
class BandejaDeSalida {

    /**
     * Lo que tarda un mensaje en llegar. Es uno para todos mientras los socios sean un eco; cuando cada socio
     * traiga su latencia, este numero sera el que use el que no diga nada.
     */
    static final int LATENCIA = 1;

    private final MensajeSalienteRepository mensajeSalienteRepository;
    private final Bitacora bitacora;
    private final JsonMapper json;

    /**
     * Escribe el mensaje en la bandeja con los campos que declara, tomados de las variables del caso. Un campo que
     * el caso no tiene viaja vacio y queda anotado: el socio recibira el hueco, y quien lea la linea de tiempo
     * sabra por que.
     */
    MensajeSaliente enviar(Caso caso, MensajeDeLaVersion mensaje, VariablesDelCaso variables, Momento momento) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        for (CampoDeMensaje campo : mensaje.campos()) {
            Object valor = variables.valor(campo.nombre()).orElse(null);
            cuerpo.put(comoViaja(campo.nombre()), valor);
            if (valor == null) {
                bitacora.anotar(caso, momento.tick(), TipoEventoCaso.VARIABLE_AUSENTE,
                        "\"" + mensaje.nombre() + "\" lleva el campo \"" + campo.nombre()
                                + "\" y el caso no lo tiene, asi que viaja vacio.");
            }
        }
        MensajeSaliente saliente = mensajeSalienteRepository.save(MensajeSaliente.builder()
                .empresa(caso.getEmpresa())
                .caso(caso)
                .mensajeId(mensaje.id())
                .nombre(mensaje.nombre())
                .poolDestinoNombre(mensaje.poolDestinoNombre())
                .integracion(mensaje.integracion())
                .tipoDestino(mensaje.tipoDestino())
                .clave(clave(caso, mensaje, variables))
                .cuerpo(json.writeValueAsString(cuerpo))
                .estado(EstadoMensajeSaliente.PENDIENTE)
                .tickCreacion(momento.tick())
                .tickEntrega(momento.tick() + LATENCIA)
                .intentos(0)
                .fecha(LocalDateTime.now())
                .build());
        bitacora.anotar(caso, momento.tick(), TipoEventoCaso.MENSAJE_ENVIADO,
                "\"" + mensaje.nombre() + "\" sale hacia \"" + mensaje.poolDestinoNombre() + "\".");
        return saliente;
    }

    /**
     * Un campo se busca por su nombre entre las variables del caso, y un nombre con puntos baja por el camino,
     * igual que en una condicion. En el cuerpo viaja con su ultimo tramo, que es el nombre por el que lo conoce el
     * participante del otro lado: dentro del caso es "order.orderId" y fuera, "orderId".
     */
    private static String comoViaja(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto < 0 ? nombre : nombre.substring(punto + 1);
    }

    /**
     * Con que valor encontrara su caso la respuesta. Es el campo de correlacion del mensaje tomado de las
     * variables; si el mensaje no dice campo o el caso no lo tiene, la referencia del caso, que es lo que el caso
     * usa para identificarse ante todo el mundo.
     */
    private static String clave(Caso caso, MensajeDeLaVersion mensaje, VariablesDelCaso variables) {
        if (mensaje.campoDeCorrelacion() == null) {
            return caso.getReferencia();
        }
        return variables.valor(mensaje.campoDeCorrelacion())
                .map(String::valueOf)
                .orElseGet(caso::getReferencia);
    }
}
