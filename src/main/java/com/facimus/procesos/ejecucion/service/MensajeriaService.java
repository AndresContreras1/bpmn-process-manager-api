package com.facimus.procesos.ejecucion.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajeEntranteResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajeSalienteResponse;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;

/** D9: las dos bandejas de una tienda. Lo que el proceso mando, lo que le llego y a que caso fue a parar. */
public interface MensajeriaService {

    /**
     * Un mensaje que llega al proceso: se guarda, se busca a que caso corresponde y, si alguien lo estaba
     * esperando, el caso sigue. Repetir la clave externa responde lo de la primera vez sin volver a procesarlo.
     */
    MensajeEntranteResponse recibir(Long empresaId, Long procesoId, DatosDelEntrante datos);

    PageResponse<MensajeSalienteResponse> bandejaDeSalida(Long empresaId, Long procesoId,
            EstadoMensajeSaliente estado, Pageable pagina);

    PageResponse<MensajeEntranteResponse> bandejaDeEntrada(Long empresaId, Long procesoId,
            ResultadoCorrelacion resultado, Pageable pagina);

    /** Lo que un caso mando y lo que recibio, en el orden en que paso. */
    List<MensajeSalienteResponse> salientesDelCaso(Long empresaId, Long casoId);

    List<MensajeEntranteResponse> entrantesDelCaso(Long empresaId, Long casoId);
}
