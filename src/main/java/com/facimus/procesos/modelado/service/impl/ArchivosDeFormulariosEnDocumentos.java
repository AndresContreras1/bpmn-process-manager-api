package com.facimus.procesos.modelado.service.impl;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.formularios.ArchivosDeFormularios;
import com.facimus.procesos.modelado.model.Documento;
import com.facimus.procesos.modelado.model.EstadoDocumento;
import com.facimus.procesos.modelado.repository.DocumentoRepository;
import com.facimus.procesos.modelado.repository.VersionDocumentoRepository;

import lombok.RequiredArgsConstructor;

/**
 * PR 69: un campo {@code ARCHIVO} se contesta con un documento del proceso (PR 61) que ya paso el antivirus. Otro
 * proceso, otra tienda o un archivo que sigue en revision o quedo retenido no sirven.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ArchivosDeFormulariosEnDocumentos implements ArchivosDeFormularios {

    private final DocumentoRepository documentoRepository;
    private final VersionDocumentoRepository versionDocumentoRepository;

    @Override
    public boolean disponible(Long empresaId, Long procesoId, Long documentoId) {
        return documentoRepository.findByIdAndEmpresaId(documentoId, empresaId)
                .filter(documento -> documento.getProceso().getId().equals(procesoId))
                .map(Documento::getVersionActual)
                .flatMap(numero -> versionDocumentoRepository.findByDocumento_IdAndNumeroAndEmpresaId(documentoId,
                        numero, empresaId))
                .map(version -> version.getEstado() == EstadoDocumento.DISPONIBLE)
                .orElse(false);
    }
}
