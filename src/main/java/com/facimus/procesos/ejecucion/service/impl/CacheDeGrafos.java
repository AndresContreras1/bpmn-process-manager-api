package com.facimus.procesos.ejecucion.service.impl;

import java.util.function.Supplier;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.facimus.procesos.config.CacheConfig;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * El grafo de una version publicada, guardado en memoria (D19). Esta aparte de {@link GrafosDeVersion} porque una
 * anotacion de cache solo la intercepta el proxy de Spring: llamar al metodo desde la misma clase no pasaria por el
 * y no guardaria nada.
 *
 * <p>El JSON llega como proveedor y no como texto: en un acierto no se pide, y como la version casi siempre llega
 * como proxy perezoso, eso significa que su fila -con el diagrama entero dentro- no se consulta.
 */
@Component
@RequiredArgsConstructor
class CacheDeGrafos {

    private final JsonMapper json;

    /**
     * El grafo de esa version de esa tienda. {@code sync} hace que diez peticiones a la vez sobre la misma version
     * lo armen una sola vez en lugar de diez, que es justo lo que pasa cuando entra un pico de pedidos.
     *
     * <p>Es publico a proposito: Spring solo mira las anotaciones de cache en metodos publicos, y una anotacion que
     * nadie intercepta no falla, simplemente no guarda nada.
     */
    @Cacheable(cacheNames = CacheConfig.GRAFOS, key = "#empresaId + ':' + #versionId", sync = true)
    public GrafoDeVersion grafo(Long empresaId, Long versionId, Supplier<String> definicion) {
        return GrafoDeVersion.de(json.readValue(definicion.get(), DiagramaResponse.class));
    }
}
