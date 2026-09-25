package com.facimus.procesos.integracion.simulado;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.facimus.procesos.common.condiciones.CondicionMalEscrita;
import com.facimus.procesos.common.condiciones.EvaluadorDeCondiciones;
import com.facimus.procesos.common.condiciones.Variables;
import com.facimus.procesos.ejecucion.puerto.MensajeParaElSocio;
import com.facimus.procesos.ejecucion.puerto.ParametrosDeSimulacion;
import com.facimus.procesos.ejecucion.puerto.RespuestaDelSocio;
import com.facimus.procesos.ejecucion.puerto.RespuestaEntrante;
import com.facimus.procesos.ejecucion.puerto.SocioSimulado;
import com.facimus.procesos.modelado.model.Integracion;

/**
 * D7: la pasarela de pagos, simulada. No cobra nada y no habla con nadie: recibe la peticion de autorizacion,
 * decide si la aprueba y contesta el mensaje que el diagrama dice que contesta.
 *
 * <p>Decide en dos pasos. Primero la regla que la tienda escribio, en la misma gramatica que las condiciones de un
 * gateway y leida sobre el cuerpo de lo que le mandaron: si se cumple, rechaza seguro, y eso es lo que deja decir
 * "los pedidos de mas de cinco mil se caen" sin depender de ningun azar. Si no hay regla o no se cumple, decide la
 * tasa con la semilla de la tienda, que tambien es siempre la misma.
 */
@Component
class PasarelaSimulada implements SocioSimulado {

    private static final String TOTAL = "total";

    @Override
    public Integracion integracion() {
        return Integracion.PAGOS;
    }

    @Override
    public int latencia(ParametrosDeSimulacion parametros) {
        return parametros.ticksRespuestaPagos();
    }

    @Override
    public RespuestaDelSocio recibir(MensajeParaElSocio mensaje) {
        if (mensaje.respuestaEsperada() == null) {
            // Un mensaje a la pasarela que el diagrama no espera que se conteste: llega y ya.
            return RespuestaDelSocio.llego();
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status", rechaza(mensaje) ? "DECLINED" : "APPROVED");
        cuerpo.put("transactionId", "SIM-PAY-" + mensaje.casoId());
        cuerpo.put("amount", mensaje.cuerpo().get(TOTAL));
        return RespuestaDelSocio.llegoYContesta(List.of(
                RespuestaEntrante.ahora(mensaje.respuestaEsperada(), mensaje.clave(), cuerpo)));
    }

    /** La regla rechaza seguro; lo que la regla no rechaza queda en manos de la tasa. */
    private static boolean rechaza(MensajeParaElSocio mensaje) {
        ParametrosDeSimulacion parametros = mensaje.parametros();
        return porLaRegla(parametros.reglaRechazoPagos(), mensaje.cuerpo())
                || SemillaDeterminista.leToca(parametros.semilla(), mensaje.casoId(), mensaje.nombre(),
                        parametros.tasaRechazoPagos());
    }

    /**
     * Una regla vacia no rechaza nada. Una mal escrita tampoco: no se puede guardar, porque se comprueba al
     * guardarla, y si alguna llegara aqui es mejor que la tasa decida a que la pasarela se caiga a media demo.
     */
    private static boolean porLaRegla(String regla, Map<String, Object> cuerpo) {
        if (regla == null || regla.isBlank()) {
            return false;
        }
        try {
            return EvaluadorDeCondiciones.compilar(regla).seCumple(Variables.de(cuerpo));
        } catch (CondicionMalEscrita noCompila) {
            return false;
        }
    }
}
