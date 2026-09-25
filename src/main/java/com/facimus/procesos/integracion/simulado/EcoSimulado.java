package com.facimus.procesos.integracion.simulado;

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
 * El socio que atiende a cualquier participante que no tenga uno propio: recibe lo que le mandan, lo da por
 * entregado y, si el diagrama dice con que mensaje se contesta, lo contesta con el cuerpo vacio y la misma clave.
 *
 * <p>Con eso basta para que un caso que espera una respuesta la reciba y siga, que es lo que hace falta para
 * probar la correlacion y el reloj. Lo que cada socio contesta de verdad (si el pago se aprueba, si el envio se
 * pierde) es del PR de los socios simulados; este no decide nada.
 */
@Component
class EcoSimulado implements SocioSimulado {

    @Override
    public Integracion integracion() {
        return Integracion.NINGUNA;
    }

    /** Un participante sin socio propio contesta enseguida: no hay nada que simular sobre lo que tarda. */
    @Override
    public int latencia(ParametrosDeSimulacion parametros) {
        return 1;
    }

    @Override
    public RespuestaDelSocio recibir(MensajeParaElSocio mensaje) {
        if (mensaje.respuestaEsperada() == null) {
            return RespuestaDelSocio.llego();
        }
        return RespuestaDelSocio.llegoYContesta(List.of(
                new RespuestaEntrante(mensaje.respuestaEsperada(), mensaje.clave(), Map.of())));
    }
}
