package com.facimus.procesos.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * D19: la cache de lo que una version publicada dice. Publicar congela el diagrama, asi que su JSON y el grafo que
 * se arma con el son inmutables: la clave lleva el id de la version y por eso <b>no hay ninguna invalidacion</b>,
 * ni al publicar ni al retirar. Lo que cambia —cual es la version vigente de un proceso— no se guarda nunca: se
 * pregunta a la base en cada peticion, que es una consulta a un indice y sin el CLOB del diagrama.
 *
 * <p>La clave empieza por el id de la tienda. No es que haga falta para acertar —los ids de version son unicos en
 * todo el sistema—, es que asi una clave mal construida no puede devolverle a una tienda el diagrama de otra, y una
 * regla de ArquiUnit lo comprueba (MultitenenciaTest). Guardar tambien es una forma de leer.
 *
 * <p>El gestor es propio y no el que armaria Spring Boot por su cuenta: cada cache declara su tamano y su tiempo de
 * inactividad, las dos existen desde el arranque —si no, no habria metricas de una cache que nadie ha usado
 * todavia— y {@code recordStats} es lo que hace que Actuator pueda contar aciertos. Caffeine y no un JCache
 * compartido: un gestor compartido acaba siendo de quien lo configuro primero.
 *
 * <p>{@code CACHE_VERSIONES=false} no deja el gestor vacio: deja la aplicacion sin {@code @EnableCaching}, asi que
 * las anotaciones no las intercepta nadie y cada llamada vuelve a la base. Es como corren las pruebas.
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "cache.versiones.activa", havingValue = "true", matchIfMissing = true)
public class CacheConfig {

    /** El grafo ya masticado de una version publicada: nodos, arcos, condiciones compiladas y alcances. */
    public static final String GRAFOS = "grafos-de-version";

    /** El JSON del diagrama tal como se publico, que es lo que lee quien solo puede ver lo publicado. */
    public static final String DEFINICIONES = "definiciones-de-version";

    @Bean
    public CacheManager cacheManager(@Value("${cache.versiones.maximo}") long maximo,
            @Value("${cache.versiones.inactividad}") Duration inactividad) {
        CaffeineCacheManager gestor = new CaffeineCacheManager();
        gestor.registerCustomCache(GRAFOS, caffeine(maximo, inactividad).build());
        gestor.registerCustomCache(DEFINICIONES, caffeine(maximo, inactividad).build());
        return gestor;
    }

    /**
     * El tamano es el techo de versiones distintas que se recuerdan a la vez, no de tiendas: una tienda que solo
     * opera un proceso ocupa una entrada. Y el desalojo es por inactividad y no por antiguedad, porque una entrada
     * vieja que se sigue usando es justo la que hay que conservar.
     */
    private static Caffeine<Object, Object> caffeine(long maximo, Duration inactividad) {
        return Caffeine.newBuilder()
                .maximumSize(maximo)
                .expireAfterAccess(inactividad)
                .recordStats();
    }
}
