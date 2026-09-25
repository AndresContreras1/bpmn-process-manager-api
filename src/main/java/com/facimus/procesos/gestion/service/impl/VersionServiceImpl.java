package com.facimus.procesos.gestion.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.dto.response.VersionResponse;
import com.facimus.procesos.gestion.mapper.VersionProcesoMapper;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.repository.VersionProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.Instantanea;
import com.facimus.procesos.gestion.service.VersionService;

import lombok.RequiredArgsConstructor;

/**
 * Una version publicada no se edita ni se borra: se agrega y, si deja de servir, se retira. El proceso guarda cual
 * es la vigente y con que huella, para no tener que consultarlas cada vez que alguien lo lee.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VersionServiceImpl implements VersionService {

    private final VersionProcesoRepository versionProcesoRepository;
    private final ProcesoRepository procesoRepository;
    private final UsuarioRepository usuarioRepository;
    private final HistorialCambioService historialCambioService;
    private final VersionProcesoMapper versionProcesoMapper;

    /** Los numeros no se reusan: si la ultima version se retiro, la siguiente sigue contando desde ella. */
    @Override
    public int siguienteNumero(Long empresaId, Long procesoId) {
        return versionProcesoRepository.ultimoNumero(procesoId, empresaId).orElse(0) + 1;
    }

    @Override
    @Transactional
    public void publicar(Proceso proceso, int numero, Long usuarioId, Instantanea instantanea) {
        versionProcesoRepository.save(VersionProceso.builder()
                .empresa(proceso.getEmpresa())
                .proceso(proceso)
                .numero(numero)
                .estado(EstadoVersion.VIGENTE)
                .fechaPublicacion(LocalDateTime.now())
                .publicadoPor(usuarioId)
                .huella(instantanea.huella())
                .definicion(instantanea.definicion())
                .build());
    }

    @Override
    public List<VersionResponse> listar(Long empresaId, Long procesoId) {
        exigirProceso(empresaId, procesoId);
        return versionProcesoMapper.toResponses(
                versionProcesoRepository.findAllByProcesoIdAndEmpresaIdOrderByNumeroDesc(procesoId, empresaId));
    }

    @Override
    public VersionResponse obtener(Long empresaId, Long procesoId, int numero) {
        return versionProcesoMapper.toResponse(buscar(empresaId, procesoId, numero));
    }

    @Override
    public String definicion(Long empresaId, Long procesoId, int numero) {
        return buscar(empresaId, procesoId, numero).getDefinicion();
    }

    @Override
    public Optional<String> definicionVigente(Long empresaPropietariaId, Long procesoId) {
        return versionProcesoRepository
                .findFirstByProcesoIdAndEmpresaIdAndEstadoOrderByNumeroDesc(procesoId, empresaPropietariaId,
                        EstadoVersion.VIGENTE)
                .map(VersionProceso::getDefinicion);
    }

    /**
     * Pasa por el proceso a proposito: uno de otra tienda, o eliminado, no tiene version vigente aunque sus filas
     * sigan ahi. Asi la puerta de abrir un caso responde 404 antes de mirar nada mas.
     */
    @Override
    public Optional<VersionProceso> vigente(Long empresaId, Long procesoId) {
        exigirProceso(empresaId, procesoId);
        return versionProcesoRepository.findFirstByProcesoIdAndEmpresaIdAndEstadoOrderByNumeroDesc(procesoId,
                empresaId, EstadoVersion.VIGENTE);
    }

    @Override
    @Transactional
    public VersionResponse retirar(Long empresaId, Long procesoId, int numero, Long usuarioId) {
        VersionProceso version = buscar(empresaId, procesoId, numero);
        if (version.getEstado() == EstadoVersion.RETIRADA) {
            throw new ReglaNegocioException("La versión " + numero + " ya está retirada.");
        }
        version.setEstado(EstadoVersion.RETIRADA);
        versionProcesoRepository.save(version);

        Proceso proceso = exigirProceso(empresaId, procesoId);
        anotarLaVigente(proceso, empresaId);
        historialCambioService.registrar(proceso, autor(empresaId, usuarioId), "Versión " + numero + " retirada.");
        return versionProcesoMapper.toResponse(version);
    }

    /**
     * La vigente es la ultima que sigue en pie. Retirar la del medio no cambia nada; retirar la ultima devuelve el
     * proceso a la anterior, y retirarlas todas lo deja publicado pero sin nada que leer, hasta que se publique otra.
     */
    private void anotarLaVigente(Proceso proceso, Long empresaId) {
        Optional<VersionProceso> vigente = versionProcesoRepository
                .findFirstByProcesoIdAndEmpresaIdAndEstadoOrderByNumeroDesc(proceso.getId(), empresaId,
                        EstadoVersion.VIGENTE);
        proceso.setVersionPublicada(vigente.map(VersionProceso::getNumero).orElse(null));
        proceso.setHuellaPublicada(vigente.map(VersionProceso::getHuella).orElse(null));
        procesoRepository.save(proceso);
    }

    private VersionProceso buscar(Long empresaId, Long procesoId, int numero) {
        exigirProceso(empresaId, procesoId);
        return versionProcesoRepository.findByProcesoIdAndNumeroAndEmpresaId(procesoId, numero, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Versión no encontrada."));
    }

    /** Las versiones son del proceso: un proceso de otra tienda, o eliminado, no tiene ninguna que mostrar. */
    private Proceso exigirProceso(Long empresaId, Long procesoId) {
        return procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
    }

    private Usuario autor(Long empresaId, Long usuarioId) {
        return usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }
}
