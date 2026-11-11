package com.facimus.procesos.modelado.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.dto.response.RevisionBorradorResponse;
import com.facimus.procesos.modelado.model.RevisionBorrador;

/** PR 40: los puntos de guardado de un borrador. Listarlos no carga el XML. */
public interface RevisionBorradorRepository extends RepositorioTenant<RevisionBorrador> {

    @Query("select new com.facimus.procesos.modelado.dto.response.RevisionBorradorResponse(r.id, r.motivo, r.huella, "
            + "r.usuarioId, r.fecha) from RevisionBorrador r where r.procesoId = :procesoId "
            + "and r.empresa.id = :empresaId order by r.id desc")
    List<RevisionBorradorResponse> resumenes(@Param("procesoId") Long procesoId, @Param("empresaId") Long empresaId);

    Optional<RevisionBorrador> findByIdAndProcesoIdAndEmpresaId(Long id, Long procesoId, Long empresaId);

    /** La ultima, sin su XML, para saber si ya toca otra. */
    @Query("select new com.facimus.procesos.modelado.dto.response.RevisionBorradorResponse(r.id, r.motivo, r.huella, "
            + "r.usuarioId, r.fecha) from RevisionBorrador r where r.procesoId = :procesoId "
            + "and r.empresa.id = :empresaId order by r.id desc")
    List<RevisionBorradorResponse> ultimas(@Param("procesoId") Long procesoId, @Param("empresaId") Long empresaId,
            Pageable pagina);

    /** El id de la revision en esa posicion, de la mas nueva a la mas vieja: de ahi para atras sobran. */
    @Query("select r.id from RevisionBorrador r where r.procesoId = :procesoId and r.empresa.id = :empresaId "
            + "order by r.id desc")
    List<Long> idsDesde(@Param("procesoId") Long procesoId, @Param("empresaId") Long empresaId, Pageable pagina);

    @Modifying
    @Query("delete from RevisionBorrador r where r.procesoId = :procesoId and r.empresa.id = :empresaId "
            + "and r.id <= :hasta")
    int borrarHasta(@Param("procesoId") Long procesoId, @Param("empresaId") Long empresaId, @Param("hasta") Long hasta);
}
