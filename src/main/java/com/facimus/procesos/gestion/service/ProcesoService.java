package com.facimus.procesos.gestion.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoDetalleResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoLectura;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.ModoProceso;

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
     * PR 64: congela el borrador para una solicitud de revision, con las mismas condiciones que publicar: el
     * diagnostico limpio y algo distinto de la version vigente. Si no se cumplen, 409.
     */
    BorradorCongelado congelar(Long empresaId, Long procesoId);

    /**
     * PR 64: por que lo congelado ya no se publicaria: la version vigente cambio desde que se congelo, o el diagnostico
     * ya no esta limpio. Vacio si se puede publicar.
     */
    Optional<String> impedimento(Long empresaId, Long procesoId, BorradorCongelado congelado);

    /**
     * PR 64: publica lo congelado por el mismo camino que publicar directo (D2), sin volver a mirar el borrador de hoy.
     * Si ya no se puede, no publica nada y devuelve por que, sin deshacer lo que la transaccion de quien llama hizo.
     *
     * @param usuarioId quien completo la aprobacion: queda como quien publico
     * @param solicitudId la solicitud que se publica, para la bitacora
     */
    Optional<String> publicarCongelado(Long empresaId, Long procesoId, Long usuarioId, BorradorCongelado congelado,
            Long solicitudId);

    /**
     * PR 64: "sigue vigente". Lo confirma su dueno, o quien puede administrar el proceso; queda en la bitacora con la
     * nota, y la proxima revision pasa a hoy mas los meses entre revisiones del proceso.
     */
    ProcesoResponse recertificar(Long empresaId, Long procesoId, Long usuarioId, String nota);
}
