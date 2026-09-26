package com.facimus.procesos.integracion.simulado;

import org.springframework.stereotype.Component;

import com.facimus.procesos.ejecucion.puerto.MensajeParaElSocio;
import com.facimus.procesos.ejecucion.puerto.ParametrosDeSimulacion;
import com.facimus.procesos.ejecucion.puerto.RespuestaDelSocio;
import com.facimus.procesos.ejecucion.puerto.SocioSimulado;
import com.facimus.procesos.modelado.model.Integracion;

/**
 * D7: quien manda los correos, los mensajes de texto y las llamadas, simulado. Es el unico socio que no contesta
 * nada: una notificacion se entrega o no se entrega, y ya.
 *
 * <p>Cuando no se entrega es cuando se pone interesante, porque ahi manda el siFalla del mensaje: el proceso sigue
 * por donde iba, se desvia a quien atiende el problema, o el pedido se da por perdido. Esa rama la escribio el PR
 * de la mensajeria y hasta ahora no tenia quien la disparara.
 */
@Component
class NotificadorSimulado implements SocioSimulado {

    @Override
    public Integracion integracion() {
        return Integracion.NOTIFICACIONES;
    }

    /** Un correo sale enseguida: lo que tarda en leerlo quien lo recibe no es cosa del proceso. */
    @Override
    public int latencia(ParametrosDeSimulacion parametros) {
        return 1;
    }

    @Override
    public RespuestaDelSocio recibir(MensajeParaElSocio mensaje) {
        ParametrosDeSimulacion parametros = mensaje.parametros();
        boolean falla = SemillaDeterminista.leToca(parametros.semilla(), mensaje.casoId(), mensaje.nombre(),
                parametros.tasaFalloNotificaciones());
        return falla ? RespuestaDelSocio.noLlego("el destinatario no la recibio") : RespuestaDelSocio.llego();
    }
}
