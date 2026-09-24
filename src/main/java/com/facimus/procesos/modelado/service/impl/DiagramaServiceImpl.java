package com.facimus.procesos.modelado.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.dto.response.ProcesoLectura;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.service.DiagramaService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * El proceso entra por la puerta de lectura, asi que un proceso ajeno o eliminado responde 404 antes de armar nada.
 * La tienda duena ve su modelo vivo, con el aviso de si tiene cambios sin publicar; una invitada ve la version
 * vigente, que es lo unico que la otra tienda dio por bueno (HU-23 y D2).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiagramaServiceImpl implements DiagramaService {

    private final ProcesoService procesoService;
    private final VersionService versionService;
    private final ArmadoDelDiagrama armado;
    private final JsonMapper json;

    @Override
    public DiagramaResponse obtener(Long empresaId, Long procesoId) {
        ProcesoLectura lectura = procesoService.obtenerParaLectura(empresaId, procesoId);
        if (lectura.compartido()) {
            return loPublicado(lectura, procesoId);
        }
        DiagramaResponse diagrama = armado.armar(lectura.proceso(), false, lectura.empresaPropietariaId());
        return diagrama.conProceso(lectura.proceso().conBorradorPendiente(tieneCambios(diagrama, lectura)), false);
    }

    /** Sin ninguna version vigente no hay nada que ensenarle a la invitada: para ella el proceso todavia no existe. */
    private DiagramaResponse loPublicado(ProcesoLectura lectura, Long procesoId) {
        String definicion = versionService.definicionVigente(lectura.empresaPropietariaId(), procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
        DiagramaResponse publicado = json.readValue(definicion, DiagramaResponse.class);
        // El dibujo es el del dia que se publico; el encabezado, el proceso como esta hoy.
        return publicado.conProceso(lectura.proceso(), true);
    }

    /**
     * Comparar la huella de lo que se acaba de armar con la de la version vigente no cuesta ninguna consulta mas: el
     * diagrama ya esta aqui y la huella publicada llego con la puerta de lectura.
     */
    private Boolean tieneCambios(DiagramaResponse diagrama, ProcesoLectura lectura) {
        if (lectura.proceso().versionPublicada() == null) {
            return false;
        }
        return !Huella.de(DiagramaCanonico.de(diagrama, json)).equals(lectura.huellaPublicada());
    }
}
