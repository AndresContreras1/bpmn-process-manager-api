package com.facimus.procesos.integracion.simulado;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.facimus.procesos.ejecucion.puerto.MensajeParaElSocio;
import com.facimus.procesos.ejecucion.puerto.ParametrosDeSimulacion;
import com.facimus.procesos.ejecucion.puerto.RespuestaDelSocio;
import com.facimus.procesos.ejecucion.puerto.RespuestaEntrante;
import com.facimus.procesos.ejecucion.puerto.SocioSimulado;
import com.facimus.procesos.modelado.model.Integracion;

/**
 * D7: el transportista, simulado. Recoge el paquete y contesta con la guia; despues, si el diagrama declara que
 * ese participante avisa por su cuenta, deja dicha la confirmacion de entrega para dentro de unos ticks.
 *
 * <p>Es el unico socio que contesta dos veces y en dos momentos, y por eso existe la respuesta que tarda: un
 * paquete no se entrega en el mismo instante en que lo recogen, y un pedido que se queda esperando la
 * confirmacion es justo lo que hay que poder ver en el tablero.
 */
@Component
class TransportistaSimulado implements SocioSimulado {

    @Override
    public Integracion integracion() {
        return Integracion.TRANSPORTE;
    }

    @Override
    public int latencia(ParametrosDeSimulacion parametros) {
        return parametros.ticksRespuestaTransporte();
    }

    @Override
    public RespuestaDelSocio recibir(MensajeParaElSocio mensaje) {
        List<RespuestaEntrante> respuestas = new ArrayList<>();
        if (mensaje.respuestaEsperada() != null) {
            respuestas.add(RespuestaEntrante.ahora(mensaje.respuestaEsperada(), mensaje.clave(),
                    Map.of("trackingNumber", guia(mensaje), "status", "CREATED")));
        }
        if (mensaje.avisoPosterior() != null) {
            respuestas.add(new RespuestaEntrante(mensaje.avisoPosterior(), mensaje.clave(), entrega(mensaje),
                    mensaje.parametros().ticksEntrega()));
        }
        return respuestas.isEmpty() ? RespuestaDelSocio.llego() : RespuestaDelSocio.llegoYContesta(respuestas);
    }

    /** Un envio perdido llega igual, pero contando que se perdio: el proceso decide que hacer con eso. */
    private static Map<String, Object> entrega(MensajeParaElSocio mensaje) {
        ParametrosDeSimulacion parametros = mensaje.parametros();
        boolean perdido = SemillaDeterminista.leToca(parametros.semilla(), mensaje.casoId(), mensaje.nombre(),
                parametros.tasaPerdidaEnvios());
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("trackingNumber", guia(mensaje));
        cuerpo.put("status", perdido ? "LOST" : "DELIVERED");
        return cuerpo;
    }

    private static String guia(MensajeParaElSocio mensaje) {
        return "SIM-TRK-" + mensaje.casoId();
    }
}
