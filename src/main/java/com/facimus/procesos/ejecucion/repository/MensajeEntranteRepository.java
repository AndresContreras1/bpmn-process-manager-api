package com.facimus.procesos.ejecucion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.ejecucion.model.MensajeEntrante;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;

/**
 * La bandeja de entrada de una tienda. Las dos consultas sin cuerpo son las que declara la entidad (D22), igual
 * que las de la bandeja de salida.
 */
public interface MensajeEntranteRepository extends RepositorioTenant<MensajeEntrante> {

    /** Lo que ha llegado a un proceso, entero o solo lo que acabo de una manera. */
    Page<MensajeEntrante> bandejaDeEntrada(@Param("empresaId") Long empresaId, @Param("procesoId") Long procesoId,
            @Param("resultado") ResultadoCorrelacion resultado, Pageable pagina);

    /**
     * Los que llegaron antes de que nadie los esperara. Se vuelven a mirar cada vez que el caso avanza, que es
     * cuando puede haber aparecido el nodo que los espera.
     */
    List<MensajeEntrante> enEspera(@Param("empresaId") Long empresaId, @Param("procesoId") Long procesoId,
            @Param("nombre") String nombre);

    /** Si ese mensaje ya entro: repetir la clave externa responde lo de la primera vez, no lo procesa otra vez. */
    Optional<MensajeEntrante> findByEmpresaIdAndClaveExterna(Long empresaId, String claveExterna);

    /** Lo que un caso recibio, para su detalle. */
    List<MensajeEntrante> findAllByCasoIdAndEmpresaIdOrderByIdAsc(Long casoId, Long empresaId);
}
