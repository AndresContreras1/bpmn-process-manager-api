package com.facimus.procesos.gestion.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoDetalleResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoLectura;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;

/** HU-04 a HU-07: creacion, edicion, eliminacion logica y consulta de procesos. */
public interface ProcesoService {

    PageResponse<ProcesoResponse> buscar(Long empresaId, String nombre, EstadoProceso estado, String categoria,
            Pageable pageable);

    ProcesoResponse crear(Long empresaId, Long usuarioId, String nombre, String descripcion, String categoria);

    ProcesoResponse obtener(Long empresaId, Long procesoId);

    /** La puerta de lectura (HU-23): el proceso propio o el que otra empresa le comparte a esta, nunca para cambiarlo. */
    ProcesoLectura obtenerParaLectura(Long empresaId, Long procesoId);

    ProcesoDetalleResponse obtenerDetalle(Long empresaId, Long procesoId);

    List<HistorialCambioResponse> listarHistorial(Long empresaId, Long procesoId);

    ProcesoResponse editarDatos(Long empresaId, Long procesoId, Long usuarioId, String nombre, String descripcion,
            String categoria, Long version);

    ProcesoResponse cambiarEstado(Long empresaId, Long procesoId, Long usuarioId, EstadoProceso nuevoEstado,
            Long version);

    void eliminarLogico(Long empresaId, Long procesoId, Long usuarioId);
}
