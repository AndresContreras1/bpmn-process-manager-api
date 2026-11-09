package com.facimus.procesos.common.auditoria;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * PR 31: algo que paso en una tienda y que la bitacora tiene que guardar. Lleva lo que la peticion sabia cuando
 * paso (la IP, el navegador, el id de la peticion y el de la traza), porque quien lo anota lo hace despues, en otro
 * hilo. El detalle son datos, nunca nombres ni correos: la bitacora no se puede cambiar, asi que no guarda nada que
 * haya que seudonimizar despues.
 */
public record SucesoAuditable(Long empresaId, TipoDeActor actorTipo, Long actorId, String accion, String recurso,
        Long recursoId, boolean exito, Map<String, Object> detalle, String ip, String userAgent, String requestId,
        String traceId, LocalDateTime momento) {

    /** Quien lo hizo: una persona, la propia API o, cuando lleguen, el operador o una clave de API. */
    public enum TipoDeActor {
        USUARIO,
        SISTEMA,
        OPERADOR,
        CLAVE_DE_API
    }
}
