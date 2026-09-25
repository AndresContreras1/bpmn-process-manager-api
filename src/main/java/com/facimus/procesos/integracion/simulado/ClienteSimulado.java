package com.facimus.procesos.integracion.simulado;

import org.springframework.stereotype.Component;

import com.facimus.procesos.ejecucion.puerto.MensajeParaElSocio;
import com.facimus.procesos.ejecucion.puerto.ParametrosDeSimulacion;
import com.facimus.procesos.ejecucion.puerto.RespuestaDelSocio;
import com.facimus.procesos.ejecucion.puerto.SocioSimulado;
import com.facimus.procesos.modelado.model.Integracion;

/**
 * D7: el cliente, simulado. Lo que la tienda le manda le llega siempre: un aviso de que su pedido se cancelo no se
 * pierde por el camino, y si se perdiera seria culpa de quien lo manda, que es el notificador y no el.
 *
 * <p>Los pedidos no los hace aqui. Un cliente que compra no esta contestando a nada: lo hace quien pide una tanda
 * de pedidos simulados, y desde ahi entran por la misma puerta que cualquier otro mensaje.
 */
@Component
class ClienteSimulado implements SocioSimulado {

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
}
