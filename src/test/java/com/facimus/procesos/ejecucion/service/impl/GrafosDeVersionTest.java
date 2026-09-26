package com.facimus.procesos.ejecucion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.modelado.service.DiagramaArmado;

import tools.jackson.databind.json.JsonMapper;

/**
 * La instantanea se guarda como el JSON que devuelve GET /procesos/{id}/diagrama, asi que volver a leerla tiene que
 * dar el mismo diagrama. Si el dia de manana cambia la forma de un DTO, esto se entera antes que un caso a medias.
 *
 * <p>Aqui no hay cache: sin el proxy de Spring, la anotacion no la intercepta nadie y cada llamada arma el grafo.
 * Lo que la cache hace de verdad se prueba con la aplicacion arrancada, en CacheDeVersionesTest.
 */
class GrafosDeVersionTest {

    private static final Long EMPRESA = 7L;

    private final JsonMapper json = JsonMapper.builder().build();

    private final GrafosDeVersion grafos = new GrafosDeVersion(new CacheDeGrafos(json));

    @Test
    @DisplayName("El diagrama guardado al publicar se relee y da el mismo grafo")
    void instantanea_seRelee() {
        DiagramaArmado armado = DiagramaArmado.demo();

        GrafoDeVersion grafo = grafos.del(EMPRESA, version(json.writeValueAsString(armado.diagrama())));

        assertThat(grafo.poolDeLaTienda()).contains(armado.id(DiagramaArmado.TIENDA));
        assertThat(grafo.inicioPorMensaje()).get()
                .extracting(NodoDeLaVersion::nombre).isEqualTo("Order received");
        assertThat(grafo.salidasDe(armado.id("Payment approved?"))).hasSize(2);
        assertThat(grafo.nodo(armado.id("Pick and pack items"))).get()
                .extracting(NodoDeLaVersion::esTareaDeUsuario).isEqualTo(true);
    }

    private static VersionProceso version(String definicion) {
        return VersionProceso.builder().id(1L).numero(1).definicion(definicion).build();
    }
}
