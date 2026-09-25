package com.facimus.procesos.ejecucion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.ejecucion.dto.response.MensajesPorResultadoResponse;
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
     * Lo que este tick puede recoger: lo que llego antes de que nadie lo esperara, y lo que un socio dejo dicho
     * para un tick que ya paso. Del primero lo que cambia es el caso; del segundo, que ya le toca.
     */
    List<MensajeEntrante> pendientesDeLaTienda(@Param("empresaId") Long empresaId, @Param("tick") int tick);

    /** Lo que llego, por lo que se hizo con ello. Una consulta agrupada, no una por resultado. */
    @Query("""
            select new com.facimus.procesos.ejecucion.dto.response.MensajesPorResultadoResponse(
                    m.resultado, count(m))
            from MensajeEntrante m
            where m.empresa.id = :empresaId
              and (:procesoId is null or m.proceso.id = :procesoId)
            group by m.resultado
            order by m.resultado
            """)
    List<MensajesPorResultadoResponse> entrantesPorResultado(@Param("empresaId") Long empresaId,
            @Param("procesoId") Long procesoId);

    /** Cuantos hay con ese resultado, para el panel de simulacion. */
    long countByEmpresaIdAndResultado(Long empresaId, ResultadoCorrelacion resultado);

    /** Si ese mensaje ya entro: repetir la clave externa responde lo de la primera vez, no lo procesa otra vez. */
    Optional<MensajeEntrante> findByEmpresaIdAndClaveExterna(Long empresaId, String claveExterna);

    /** Lo que un caso recibio, para su detalle. */
    List<MensajeEntrante> findAllByCasoIdAndEmpresaIdOrderByIdAsc(Long casoId, Long empresaId);
}
