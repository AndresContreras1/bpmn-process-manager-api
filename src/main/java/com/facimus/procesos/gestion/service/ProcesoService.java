package com.facimus.procesos.gestion.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoDetalleResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoLectura;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.gestion.model.VersionProceso;

/** HU-04 a HU-07: creacion, edicion, eliminacion logica y consulta de procesos. */
public interface ProcesoService {

    PageResponse<ProcesoResponse> buscar(Long empresaId, String nombre, EstadoProceso estado, String categoria,
            boolean incluirInactivos, Pageable pageable);

    /** PR 59: la lista con los filtros del repositorio. */
    PageResponse<ProcesoResponse> buscar(Long empresaId, FiltrosDeProceso filtros, Pageable pageable);

    /** PR 59: donde esta archivado y quien responde por el. La carpeta y el dueno tienen que ser de la empresa. */
    ProcesoResponse editarFicha(Long empresaId, Long procesoId, Long usuarioId, DatosDeFicha ficha);

    List<String> etiquetasEnUso(Long empresaId);

    ProcesoResponse crear(Long empresaId, Long usuarioId, String nombre, String descripcion, String categoria);

    ProcesoResponse obtener(Long empresaId, Long procesoId, boolean incluirInactivos);

    /** La puerta de lectura (HU-23): el proceso propio o el que otra empresa le comparte a esta, nunca para cambiarlo. */
    ProcesoLectura obtenerParaLectura(Long empresaId, Long procesoId);

    ProcesoDetalleResponse obtenerDetalle(Long empresaId, Long procesoId, boolean incluirInactivos);

    List<HistorialCambioResponse> listarHistorial(Long empresaId, Long procesoId);

    ProcesoResponse editarDatos(Long empresaId, Long procesoId, Long usuarioId, String nombre, String descripcion,
            String categoria, Long version);

    ProcesoResponse cambiarEstado(Long empresaId, Long procesoId, Long usuarioId, EstadoProceso nuevoEstado,
            Long version);

    /**
     * PR 55: si el proceso se ejecuta o solo se documenta. Es parte del modelo, asi que cuenta al publicar: una
     * version documental no abre casos aunque el borrador haya vuelto a ser ejecutable.
     */
    ProcesoResponse cambiarModo(Long empresaId, Long procesoId, Long usuarioId, ModoProceso modo, Long version);

    void eliminarLogico(Long empresaId, Long procesoId, Long usuarioId);

    /**
     * PR 68: el borrador de hoy congelado en una version de prueba, para correrlo sin publicarlo. Pide lo mismo que
     * publicar (sin errores de diagnostico) menos que haya cambios: probar dos veces lo mismo es legitimo.
     */
    VersionProceso versionDePrueba(Long empresaId, Long procesoId, Long usuarioId);
}
