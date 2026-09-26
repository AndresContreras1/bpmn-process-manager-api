package com.facimus.procesos.gestion.service.impl;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.config.CacheConfig;
import com.facimus.procesos.gestion.repository.VersionProcesoRepository;

import lombok.RequiredArgsConstructor;

/**
 * El diagrama de una version publicada, guardado en memoria (D19). Publicar congela el diagrama y nada lo vuelve a
 * escribir, asi que la entrada no se invalida nunca: la clave es la version, no el proceso.
 *
 * <p>Quien llama ya comprobo que quien pregunta puede leer ese proceso, y ya averiguo -contra la base, en esta misma
 * peticion- cual es la version que le corresponde. Esta clase solo pone el texto; el permiso y la vigencia no se
 * guardan, porque las dos cosas cambian.
 *
 * <p>Esta aparte del service porque una anotacion de cache la intercepta el proxy de Spring: llamar al metodo desde
 * la misma clase no pasaria por el. Y es publica por lo mismo: Spring solo mira estas anotaciones en metodos
 * publicos, y la que nadie intercepta no falla, simplemente no guarda nada.
 */
@Component
@RequiredArgsConstructor
class DefinicionesDeVersion {

    private final VersionProcesoRepository versionProcesoRepository;

    @Cacheable(cacheNames = CacheConfig.DEFINICIONES, key = "#empresaId + ':' + #versionId", sync = true)
    public String de(Long empresaId, Long versionId) {
        return versionProcesoRepository.definicionDe(versionId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Versión no encontrada."));
    }
}
