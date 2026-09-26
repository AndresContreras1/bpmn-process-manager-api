package com.facimus.procesos.integracion.simulado;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.facimus.procesos.ejecucion.puerto.GeneradorDePedidos;
import com.facimus.procesos.ejecucion.puerto.MensajeParaElSocio;
import com.facimus.procesos.ejecucion.puerto.ParametrosDeSimulacion;
import com.facimus.procesos.ejecucion.puerto.RespuestaDelSocio;
import com.facimus.procesos.ejecucion.puerto.SocioSimulado;
import com.facimus.procesos.modelado.model.Integracion;

/**
 * D7: el cliente, simulado. Hace dos cosas que no se parecen: recibe lo que la tienda le manda, que le llega
 * siempre, y compra, que es lo que pasa cuando alguien pide una tanda de pedidos simulados.
 *
 * <p>Que le llegue siempre es a proposito: si un aviso se pierde es culpa de quien lo manda, que es el proveedor
 * de notificaciones y no el, y darle una tasa de fallo tambien al cliente seria contar dos veces lo mismo.
 *
 * <p>Los pedidos que inventa son cuerpos de mensaje y nada mas. Entran despues por la misma puerta que cualquier
 * otro mensaje que llega, y por eso un pedido simulado y uno de verdad recorren exactamente el mismo camino.
 */
@Component
class ClienteSimulado implements SocioSimulado, GeneradorDePedidos {

    private static final String TOTAL = "total";

    @Override
    public Integracion integracion() {
        return Integracion.CLIENTE;
    }

    @Override
    public int latencia(ParametrosDeSimulacion parametros) {
        return 1;
    }

    @Override
    public RespuestaDelSocio recibir(MensajeParaElSocio mensaje) {
        return RespuestaDelSocio.llego();
    }

    /**
     * Un pedido lleva su referencia y un monto. El monto sale de la semilla y no de un sorteo, para que la misma
     * tanda de la misma tienda vuelva a dar los mismos pedidos y una regla como {@code total > 5000} rechace
     * siempre a los mismos. Lo que venga en la plantilla manda: quien pide la tanda sabe mejor lo que quiere.
     */
    @Override
    public List<Map<String, Object>> pedidos(int cantidad, Map<String, Object> plantilla, List<String> referencias,
            ParametrosDeSimulacion parametros) {
        List<Map<String, Object>> cuerpos = new ArrayList<>();
        for (int numero = 0; numero < cantidad; numero++) {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("orderId", referencias.get(numero));
            cuerpo.put(TOTAL, monto(parametros.semilla(), referencias.get(numero)));
            if (plantilla != null) {
                cuerpo.putAll(plantilla);
            }
            cuerpos.add(cuerpo);
        }
        return cuerpos;
    }

    /** Entre cien y diez mil, siempre el mismo para la misma referencia de la misma tienda. */
    private static int monto(long semilla, String referencia) {
        return 100 + SemillaDeterminista.numero(semilla, (long) referencia.hashCode(), TOTAL, 9_900);
    }
}
