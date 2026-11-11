package com.facimus.procesos.modelado.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import com.facimus.procesos.modelado.model.ExtensionXml;
import com.facimus.procesos.modelado.repository.ExtensionXmlRepository;

/** PR 54: lo ajeno que el ultimo archivo importado dejo en un proceso, por su clave, para volver a exportarlo. */
final class ExtensionesGuardadas {

    private ExtensionesGuardadas() {
    }

    static Map<String, String> de(ExtensionXmlRepository repositorio, Long procesoId, Long empresaId) {
        Map<String, String> extensiones = new LinkedHashMap<>();
        for (ExtensionXml extension : repositorio.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId)) {
            extensiones.put(extension.getElemento(), extension.getXml());
        }
        return extensiones;
    }
}
