package com.facimus.procesos.gestion.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.dto.response.EmpresaInvitadaResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoRecibidoResponse;

/**
 * HU-23: una empresa comparte un proceso en solo lectura con otras, la unica excepcion al aislamiento (HU-03). Compartir,
 * listar y dejar de compartir pasan por la puerta de escritura: solo la duena encuentra su proceso.
 */
public interface ProcesoCompartidoService {

    EmpresaInvitadaResponse compartir(Long empresaId, Long procesoId, Long usuarioId, String nit);

    List<EmpresaInvitadaResponse> listarInvitadas(Long empresaId, Long procesoId);

    EmpresaInvitadaResponse obtenerInvitada(Long empresaId, Long procesoId, Long empresaInvitadaId);

    void dejarDeCompartir(Long empresaId, Long procesoId, Long empresaInvitadaId, Long usuarioId);

    /** Los procesos que otras empresas le comparten a esta, para abrirlos con el diagrama en solo lectura. */
    PageResponse<ProcesoRecibidoResponse> buscarRecibidos(Long empresaId, Pageable pageable);
}
