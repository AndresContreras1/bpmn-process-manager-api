package com.facimus.procesos.ejecucion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.config.AuditoriaConfig;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.TipoNodoCaso;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.VersionProceso;

/**
 * D22: las consultas con las que la operacion busca casos y tareas van como consultas con nombre en la entidad. Si
 * una se renombra, esto ni siquiera arranca, que es justo lo que se busca: fallar al levantar y no en produccion.
 * <p>
 * El slice conserva la base del perfil test (replace = NONE), que ya es H2 en memoria con el esquema de Flyway.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditoriaConfig.class)
class CasoRepositoryTest {

    private static final String HUELLA = "9f2c4a6b8d0e1f23456789abcdef0123456789abcdef0123456789abcdef0123";
    private static final String SIN_VARIABLES = "{}";

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CasoRepository casoRepository;

    @Autowired
    private ActividadCasoRepository actividadCasoRepository;

    private Empresa tienda;
    private Proceso proceso;
    private VersionProceso version;

    @BeforeEach
    void crearLaTiendaSuProcesoYSuVersion() {
        tienda = empresa("Tienda de casos");
        proceso = proceso(tienda, "Order fulfillment");
        version = version(proceso);
    }

    @Nested
    @DisplayName("Los casos")
    class Casos {

        @Test
        @DisplayName("abiertosPorProceso deja fuera los cerrados, los de otro proceso y los de otra tienda")
        void abiertosPorProceso_soloLosVivosDeEseProceso() {
            Caso abierto = casoRepository.save(caso("ORD-1", EstadoCaso.ABIERTO));
            Caso tambienAbierto = casoRepository.save(caso("ORD-2", EstadoCaso.ABIERTO));
            casoRepository.save(caso("ORD-3", EstadoCaso.TERMINADO));
            casoRepository.save(caso("ORD-4", EstadoCaso.CANCELADO));
            otraTiendaConSuCaso();
            em.flush();

            assertThat(casoRepository.abiertosPorProceso(tienda.getId(), proceso.getId()))
                    .extracting(Caso::getId)
                    .containsExactly(abierto.getId(), tambienAbierto.getId());
        }

        @Test
        @DisplayName("abiertosPorProceso no ve los casos de la misma tienda en otro proceso")
        void abiertosPorProceso_noVeOtroProceso() {
            casoRepository.save(caso("ORD-1", EstadoCaso.ABIERTO));
            Proceso otro = proceso(tienda, "Returns");
            casoRepository.save(Caso.builder().empresa(tienda).proceso(otro).versionProceso(version(otro))
                    .referencia("ORD-1").estado(EstadoCaso.ABIERTO).variables(SIN_VARIABLES).build());
            em.flush();

            assertThat(casoRepository.abiertosPorProceso(tienda.getId(), otro.getId())).hasSize(1);
        }

        @Test
        @DisplayName("porReferencia encuentra el caso de un pedido, tambien si ya se cerro")
        void porReferencia_encuentraElCasoDelPedido() {
            Caso pedido = casoRepository.save(caso("ORD-7", EstadoCaso.TERMINADO));
            casoRepository.save(caso("ORD-8", EstadoCaso.ABIERTO));
            em.flush();

            assertThat(casoRepository.porReferencia(tienda.getId(), proceso.getId(), "ORD-7"))
                    .extracting(Caso::getId)
                    .containsExactly(pedido.getId());
        }

        @Test
        @DisplayName("porReferencia no cruza tiendas aunque dos pedidos se llamen igual")
        void porReferencia_noCruzaTiendas() {
            casoRepository.save(caso("ORD-1", EstadoCaso.ABIERTO));
            Empresa otra = otraTiendaConSuCaso();
            em.flush();

            assertThat(casoRepository.porReferencia(otra.getId(), proceso.getId(), "ORD-1")).isEmpty();
        }

        @Test
        @DisplayName("D3: bloquear devuelve el caso de la tienda y nada de las demas")
        void bloquear_soloElCasoDeLaTienda() {
            Caso caso = casoRepository.save(caso("ORD-1", EstadoCaso.ABIERTO));
            Empresa otra = otraTiendaConSuCaso();
            em.flush();

            assertThat(casoRepository.bloquear(caso.getId(), tienda.getId())).isPresent();
            assertThat(casoRepository.bloquear(caso.getId(), otra.getId())).isEmpty();
        }

        @Test
        @DisplayName("Las variables del caso caben aunque el pedido traiga medio catalogo")
        void variables_cabenEnLaColumna() {
            String largas = "{\"items\":\"" + "x".repeat(20_000) + "\"}";

            Caso guardado = casoRepository.saveAndFlush(Caso.builder().empresa(tienda).proceso(proceso)
                    .versionProceso(version).referencia("ORD-1").estado(EstadoCaso.ABIERTO).variables(largas).build());
            em.clear();

            assertThat(em.find(Caso.class, guardado.getId()).getVariables()).isEqualTo(largas);
        }
    }

    @Nested
    @DisplayName("La bandeja de tareas")
    class Bandeja {

        private static final Long VENTAS = 1L;
        private static final Long BODEGA = 2L;

        private Caso caso;

        @BeforeEach
        void abrirUnCaso() {
            caso = casoRepository.save(caso("ORD-1", EstadoCaso.ABIERTO));
        }

        @Test
        @DisplayName("Solo trae actividades de usuario en espera: ni gateways, ni servicios, ni completadas")
        void bandeja_soloLasTareasEnEspera() {
            ActividadCaso tarea = actividadCasoRepository.save(paso("Pick and pack items", TipoNodoCaso.ACTIVIDAD,
                    "USUARIO", BODEGA, EstadoActividadCaso.EN_ESPERA));
            actividadCasoRepository.save(paso("Payment approved?", TipoNodoCaso.GATEWAY, "EXCLUSIVO", BODEGA,
                    EstadoActividadCaso.EN_ESPERA));
            actividadCasoRepository.save(paso("Cancel order", TipoNodoCaso.ACTIVIDAD, "SERVICIO", BODEGA,
                    EstadoActividadCaso.EN_ESPERA));
            actividadCasoRepository.save(paso("Receive order", TipoNodoCaso.ACTIVIDAD, "USUARIO", BODEGA,
                    EstadoActividadCaso.COMPLETADA));
            em.flush();

            assertThat(actividadCasoRepository.bandejaPorRol(tienda.getId(), null, null,
                    EstadoActividadCaso.EN_ESPERA, Paginacion.de(0, 10)))
                    .extracting(ActividadCaso::getId)
                    .containsExactly(tarea.getId());
        }

        @Test
        @DisplayName("Filtrada por rol trae solo las de ese rol; sin rol, las de todos")
        void bandeja_filtraPorRol() {
            ActividadCaso deVentas = actividadCasoRepository.save(paso("Receive order", TipoNodoCaso.ACTIVIDAD,
                    "USUARIO", VENTAS, EstadoActividadCaso.EN_ESPERA));
            ActividadCaso deBodega = actividadCasoRepository.save(paso("Pick and pack items",
                    TipoNodoCaso.ACTIVIDAD, "USUARIO", BODEGA, EstadoActividadCaso.EN_ESPERA));
            em.flush();

            assertThat(actividadCasoRepository.bandejaPorRol(tienda.getId(), BODEGA, null,
                    EstadoActividadCaso.EN_ESPERA, Paginacion.de(0, 10)))
                    .extracting(ActividadCaso::getId).containsExactly(deBodega.getId());
            assertThat(actividadCasoRepository.bandejaPorRol(tienda.getId(), null, null,
                    EstadoActividadCaso.EN_ESPERA, Paginacion.de(0, 10)))
                    .extracting(ActividadCaso::getId).containsExactly(deVentas.getId(), deBodega.getId());
        }

        @Test
        @DisplayName("Filtrada por proceso deja fuera las tareas de los casos de otro proceso")
        void bandeja_filtraPorProceso() {
            ActividadCaso deEsteProceso = actividadCasoRepository.save(paso("Pick and pack items",
                    TipoNodoCaso.ACTIVIDAD, "USUARIO", BODEGA, EstadoActividadCaso.EN_ESPERA));
            Proceso otro = proceso(tienda, "Returns");
            Caso casoDeOtroProceso = casoRepository.save(Caso.builder().empresa(tienda).proceso(otro)
                    .versionProceso(version(otro)).referencia("RET-1").estado(EstadoCaso.ABIERTO)
                    .variables(SIN_VARIABLES).build());
            actividadCasoRepository.save(ActividadCaso.builder().empresa(tienda).caso(casoDeOtroProceso).nodoId(90L)
                    .nodoNombre("Approve return").tipoNodo(TipoNodoCaso.ACTIVIDAD).subtipo("USUARIO")
                    .rolProcesoId(BODEGA).estado(EstadoActividadCaso.EN_ESPERA).build());
            em.flush();

            assertThat(actividadCasoRepository.bandejaPorRol(tienda.getId(), null, proceso.getId(),
                    EstadoActividadCaso.EN_ESPERA, Paginacion.de(0, 10)))
                    .extracting(ActividadCaso::getId).containsExactly(deEsteProceso.getId());
        }

        @Test
        @DisplayName("No cruza tiendas: la bandeja de una tienda no ve las tareas de la otra")
        void bandeja_noCruzaTiendas() {
            actividadCasoRepository.save(paso("Pick and pack items", TipoNodoCaso.ACTIVIDAD, "USUARIO", BODEGA,
                    EstadoActividadCaso.EN_ESPERA));
            Empresa otra = empresa("Tienda vecina");
            em.flush();

            assertThat(actividadCasoRepository.bandejaPorRol(otra.getId(), null, null,
                    EstadoActividadCaso.EN_ESPERA, Paginacion.de(0, 10))).isEmpty();
        }

        @Test
        @DisplayName("La bandeja se pagina y cuenta el total")
        void bandeja_pagina() {
            for (int i = 0; i < 3; i++) {
                actividadCasoRepository.save(paso("Pick and pack items", TipoNodoCaso.ACTIVIDAD, "USUARIO", BODEGA,
                        EstadoActividadCaso.EN_ESPERA));
            }
            em.flush();

            var primera = actividadCasoRepository.bandejaPorRol(tienda.getId(), BODEGA, null,
                    EstadoActividadCaso.EN_ESPERA, Paginacion.de(0, 2));

            assertThat(primera.getContent()).hasSize(2);
            assertThat(primera.getTotalElements()).isEqualTo(3);
            assertThat(primera.getTotalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("Las llegadas de un join se guardan y se releen")
        void llegadas_seGuardan() {
            ActividadCaso join = actividadCasoRepository.saveAndFlush(paso("Join", TipoNodoCaso.GATEWAY, "PARALELO",
                    null, EstadoActividadCaso.EN_ESPERA));

            join.setLlegadas(2);
            actividadCasoRepository.saveAndFlush(join);
            em.clear();

            assertThat(em.find(ActividadCaso.class, join.getId()).getLlegadas()).isEqualTo(2);
        }

        @Test
        @DisplayName("Los tokens vivos de un caso son los pendientes y los que esperan")
        void tokensVivos_pendientesYEnEspera() {
            ActividadCaso pendiente = actividadCasoRepository.save(paso("Ship order", TipoNodoCaso.ACTIVIDAD,
                    "ENVIO", null, EstadoActividadCaso.PENDIENTE));
            ActividadCaso enEspera = actividadCasoRepository.save(paso("Pick and pack items",
                    TipoNodoCaso.ACTIVIDAD, "USUARIO", BODEGA, EstadoActividadCaso.EN_ESPERA));
            actividadCasoRepository.save(paso("Receive order", TipoNodoCaso.ACTIVIDAD, "USUARIO", VENTAS,
                    EstadoActividadCaso.COMPLETADA));
            em.flush();

            assertThat(actividadCasoRepository.findAllByCasoIdAndEmpresaIdAndEstadoInOrderByIdAsc(caso.getId(),
                    tienda.getId(), java.util.List.of(EstadoActividadCaso.PENDIENTE, EstadoActividadCaso.EN_ESPERA)))
                    .extracting(ActividadCaso::getId)
                    .containsExactly(pendiente.getId(), enEspera.getId());
        }

        private ActividadCaso paso(String nombre, TipoNodoCaso tipo, String subtipo, Long rol,
                EstadoActividadCaso estado) {
            return ActividadCaso.builder().empresa(tienda).caso(caso).nodoId((long) nombre.hashCode())
                    .nodoNombre(nombre).tipoNodo(tipo).subtipo(subtipo).rolProcesoId(rol).estado(estado).build();
        }
    }

    private Caso caso(String referencia, EstadoCaso estado) {
        return Caso.builder().empresa(tienda).proceso(proceso).versionProceso(version).referencia(referencia)
                .estado(estado).variables(SIN_VARIABLES).build();
    }

    private Empresa otraTiendaConSuCaso() {
        Empresa otra = empresa("Tienda vecina");
        Proceso suyo = proceso(otra, "Order fulfillment");
        em.persistAndFlush(Caso.builder().empresa(otra).proceso(suyo).versionProceso(version(suyo))
                .referencia("ORD-1").estado(EstadoCaso.ABIERTO).variables(SIN_VARIABLES).build());
        return otra;
    }

    private Empresa empresa(String nombre) {
        return em.persistFlushFind(Empresa.builder()
                .nombre(nombre)
                .nit("900" + Math.abs(nombre.hashCode() % 1_000_000) + "-1")
                .correoContacto(nombre.replace(" ", "").toLowerCase() + "@demo.com")
                .fechaRegistro(LocalDate.now())
                .build());
    }

    private Proceso proceso(Empresa duena, String nombre) {
        return em.persistFlushFind(Proceso.builder()
                .empresa(duena)
                .nombre(nombre)
                .descripcion("Checkout to delivery")
                .categoria("Fulfillment")
                .estado(EstadoProceso.PUBLICADO)
                .activo(true)
                .build());
    }

    private VersionProceso version(Proceso publicado) {
        return em.persistFlushFind(VersionProceso.builder()
                .empresa(publicado.getEmpresa())
                .proceso(publicado)
                .numero(1)
                .estado(EstadoVersion.VIGENTE)
                .fechaPublicacion(LocalDateTime.now())
                .huella(HUELLA)
                .definicion("{}")
                .build());
    }
}
