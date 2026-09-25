package com.facimus.procesos.ejecucion.service.impl;

import static com.facimus.procesos.modelado.service.DiagramaArmado.TIENDA;
import static com.facimus.procesos.modelado.service.DiagramaArmado.VENTAS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.config.AuditoriaConfig;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EventoCaso;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.model.TipoNodoCaso;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.EventoCasoRepository;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.DiagramaArmado;

import tools.jackson.databind.json.JsonMapper;

/**
 * Una prueba por fila de la tabla de semantica (3.7), con grafos minimos armados a mano: lo que hace cada nodo
 * cuando le llega un token y cuando deja seguir al siguiente.
 * <p>
 * El motor se cablea a mano sobre el slice de JPA: necesita repositorios de verdad, porque un join se cuenta en la
 * base, pero nada de web ni de seguridad.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditoriaConfig.class)
class MotorDeProcesosTest {

    private static final Long VENDEDORES = 3L;
    private static final String SIN_VARIABLES = "{}";

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ActividadCasoRepository actividadCasoRepository;

    @Autowired
    private EventoCasoRepository eventoCasoRepository;

    private MotorDeProcesos motor;
    private Empresa tienda;
    private Proceso proceso;
    private VersionProceso version;

    @BeforeEach
    void armarElMotorYLaTienda() {
        motor = new MotorDeProcesos(actividadCasoRepository, new Bitacora(eventoCasoRepository),
                JsonMapper.builder().build());
        tienda = em.persistFlushFind(Empresa.builder().nombre("Tienda del motor").nit("900123456-1")
                .correoContacto("motor@demo.com").fechaRegistro(LocalDate.now()).build());
        proceso = em.persistFlushFind(Proceso.builder().empresa(tienda).nombre("Order fulfillment")
                .descripcion("Checkout to delivery").categoria("Fulfillment").estado(EstadoProceso.PUBLICADO)
                .activo(true).build());
        version = em.persistFlushFind(VersionProceso.builder().empresa(tienda).proceso(proceso).numero(1)
                .estado(EstadoVersion.VIGENTE).fechaPublicacion(LocalDateTime.now())
                .huella("9f2c4a6b8d0e1f23456789abcdef0123456789abcdef0123456789abcdef0123").definicion("{}")
                .build());
    }

    @Nested
    @DisplayName("Eventos")
    class Eventos {

        @Test
        @DisplayName("Un evento de inicio se completa y pone un token en cada una de sus salidas")
        void inicio_seCompletaYSigue() {
            DiagramaArmado armado = unDiagrama();
            armado.actividad(VENTAS, "Receive order", TipoActividad.USUARIO);
            armado.actividad(VENTAS, "Reserve stock", TipoActividad.USUARIO);
            armado.arco("Start", "Receive order");
            armado.arco("Start", "Reserve stock");

            Caso caso = arrancar(armado);

            assertThat(pasoPor(caso, "Start")).returns(EstadoActividadCaso.COMPLETADA, ActividadCaso::getEstado);
            assertThat(pasoPor(caso, "Receive order")).returns(EstadoActividadCaso.EN_ESPERA,
                    ActividadCaso::getEstado);
            assertThat(pasoPor(caso, "Reserve stock")).returns(EstadoActividadCaso.EN_ESPERA,
                    ActividadCaso::getEstado);
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ABIERTO);
        }

        @Test
        @DisplayName("Un evento de fin consume el token y, si no queda ninguno vivo, el caso termina")
        void fin_terminaElCaso() {
            DiagramaArmado armado = unDiagrama();
            armado.evento(VENTAS, "Done", TipoEvento.FIN);
            armado.arco("Start", "Done");

            Caso caso = arrancar(armado);

            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
            assertThat(caso.getFechaFin()).isNotNull();
            assertThat(tiposDeLaBitacora(caso)).contains(TipoEventoCaso.CASO_TERMINADO);
        }

        @Test
        @DisplayName("Un fin con otro token vivo no termina el caso")
        void fin_conOtroTokenVivo_noTermina() {
            DiagramaArmado armado = unDiagrama();
            armado.gateway(VENTAS, "Fork", TipoGateway.PARALELO);
            armado.evento(VENTAS, "Done", TipoEvento.FIN);
            armado.actividad(VENTAS, "Receive order", TipoActividad.USUARIO);
            armado.arco("Start", "Fork");
            armado.arco("Fork", "Done");
            armado.arco("Fork", "Receive order");

            Caso caso = arrancar(armado);

            assertThat(pasoPor(caso, "Done")).returns(EstadoActividadCaso.COMPLETADA, ActividadCaso::getEstado);
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ABIERTO);
            assertThat(tiposDeLaBitacora(caso)).doesNotContain(TipoEventoCaso.CASO_TERMINADO);
        }

        @Test
        @DisplayName("Un evento de mensaje intermedio pasa de largo mientras no haya mensajeria")
        void mensajeIntermedio_pasaDeLargo() {
            DiagramaArmado armado = unDiagrama();
            armado.evento(VENTAS, "Payment result received", TipoEvento.MENSAJE_INTERMEDIO);
            armado.evento(VENTAS, "Done", TipoEvento.FIN);
            armado.arco("Start", "Payment result received");
            armado.arco("Payment result received", "Done");

            Caso caso = arrancar(armado);

            assertThat(pasoPor(caso, "Payment result received"))
                    .returns(EstadoActividadCaso.COMPLETADA, ActividadCaso::getEstado);
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
        }

        @Test
        @DisplayName("Un mensaje de fin cierra su camino igual que un fin normal")
        void mensajeFin_cierraSuCamino() {
            DiagramaArmado armado = unDiagrama();
            armado.evento(VENTAS, "Order shipped", TipoEvento.MENSAJE_FIN);
            armado.arco("Start", "Order shipped");

            Caso caso = arrancar(armado);

            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
        }
    }

    @Nested
    @DisplayName("Actividades")
    class Actividades {

        @Test
        @DisplayName("Una actividad de usuario queda en espera con el rol de su lane y avisa a la bitacora")
        void usuario_quedaEnEspera() {
            DiagramaArmado armado = unDiagrama();
            armado.actividad(VENTAS, "Receive order", TipoActividad.USUARIO);
            armado.arco("Start", "Receive order");

            Caso caso = arrancar(armado);

            ActividadCaso tarea = pasoPor(caso, "Receive order");
            assertThat(tarea.getEstado()).isEqualTo(EstadoActividadCaso.EN_ESPERA);
            assertThat(tarea.getRolProcesoId()).isEqualTo(VENDEDORES);
            assertThat(tarea.esTarea()).isTrue();
            assertThat(tiposDeLaBitacora(caso)).contains(TipoEventoCaso.TAREA_CREADA);
        }

        @Test
        @DisplayName("Una actividad de servicio se completa sola y sigue")
        void servicio_seCompletaSola() {
            assertThat(unaActividadDeTipo(TipoActividad.SERVICIO)).isEqualTo(EstadoCaso.TERMINADO);
        }

        @Test
        @DisplayName("Una actividad de envio se completa de inmediato mientras no haya mensajeria")
        void envio_seCompletaDeInmediato() {
            assertThat(unaActividadDeTipo(TipoActividad.ENVIO)).isEqualTo(EstadoCaso.TERMINADO);
        }

        @Test
        @DisplayName("Una actividad de recepcion se completa de inmediato mientras no haya mensajeria")
        void recepcion_seCompletaDeInmediato() {
            assertThat(unaActividadDeTipo(TipoActividad.RECEPCION)).isEqualTo(EstadoCaso.TERMINADO);
        }

        private EstadoCaso unaActividadDeTipo(TipoActividad tipo) {
            DiagramaArmado armado = unDiagrama();
            armado.actividad(VENTAS, "Hacer algo", tipo);
            armado.evento(VENTAS, "Done", TipoEvento.FIN);
            armado.arco("Start", "Hacer algo");
            armado.arco("Hacer algo", "Done");

            Caso caso = arrancar(armado);

            assertThat(pasoPor(caso, "Hacer algo")).returns(EstadoActividadCaso.COMPLETADA,
                    ActividadCaso::getEstado);
            return caso.getEstado();
        }
    }

    @Nested
    @DisplayName("Gateways que deciden")
    class Deciden {

        @Test
        @DisplayName("Un exclusivo toma la primera salida verdadera en el orden en que las evalua")
        void exclusivo_tomaLaPrimeraVerdadera() {
            Caso caso = unExclusivo("{\"payment\":{\"status\":\"APPROVED\"},\"order\":{\"vip\":true}}",
                    armado -> {
                        armado.arcoCon("Decide", "Aprobado", "payment.status == APPROVED", 1);
                        armado.arcoCon("Decide", "Rechazado", "payment.status == DECLINED", 2);
                    });

            assertThat(pasoPor(caso, "Aprobado")).isNotNull();
            assertThat(pasosDe(caso)).extracting(ActividadCaso::getNodoNombre).doesNotContain("Rechazado");
            assertThat(tiposDeLaBitacora(caso)).contains(TipoEventoCaso.GATEWAY_DECIDIO);
        }

        @Test
        @DisplayName("Un exclusivo toma una sola salida aunque dos condiciones sean verdaderas")
        void exclusivo_tomaUnaSola() {
            Caso caso = unExclusivo("{\"order\":{\"total\":150}}", armado -> {
                armado.arcoCon("Decide", "Aprobado", "order.total > 100", 1);
                armado.arcoCon("Decide", "Rechazado", "order.total > 10", 2);
            });

            assertThat(pasosDe(caso)).extracting(ActividadCaso::getNodoNombre)
                    .contains("Aprobado").doesNotContain("Rechazado");
        }

        @Test
        @DisplayName("Sin ninguna condicion verdadera, el exclusivo se va por su salida por defecto")
        void exclusivo_sinVerdaderas_vaPorDefecto() {
            Caso caso = unExclusivo("{\"payment\":{\"status\":\"DECLINED\"}}", armado -> {
                armado.arcoCon("Decide", "Aprobado", "payment.status == APPROVED", 1);
                armado.arcoPorDefecto("Decide", "Rechazado");
            });

            assertThat(pasosDe(caso)).extracting(ActividadCaso::getNodoNombre).contains("Rechazado");
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ABIERTO);
        }

        @Test
        @DisplayName("Sin condiciones verdaderas y sin salida por defecto, el caso queda en ERROR")
        void exclusivo_sinCamino_dejaElCasoEnError() {
            Caso caso = unExclusivo("{\"payment\":{\"status\":\"PENDING\"}}", armado -> {
                armado.arcoCon("Decide", "Aprobado", "payment.status == APPROVED", 1);
                armado.arcoCon("Decide", "Rechazado", "payment.status == DECLINED", 2);
            });

            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ERROR);
            assertThat(tiposDeLaBitacora(caso)).contains(TipoEventoCaso.SIN_CAMINO);
            assertThat(pasoPor(caso, "Decide")).returns(EstadoActividadCaso.EN_ESPERA, ActividadCaso::getEstado);
            assertThat(pasosDe(caso)).extracting(ActividadCaso::getNodoNombre).doesNotContain("Aprobado");
        }

        @Test
        @DisplayName("Una condicion que pregunta por una variable que no esta lo deja anotado")
        void variableAusente_quedaEnLaBitacora() {
            Caso caso = unExclusivo(SIN_VARIABLES, armado -> {
                armado.arcoCon("Decide", "Aprobado", "payment.status == APPROVED", 1);
                armado.arcoPorDefecto("Decide", "Rechazado");
            });

            assertThat(tiposDeLaBitacora(caso)).contains(TipoEventoCaso.VARIABLE_AUSENTE);
            assertThat(detallesDeLaBitacora(caso))
                    .anyMatch(detalle -> detalle.contains("payment.status") && detalle.contains("no la tiene"));
        }

        @Test
        @DisplayName("Un inclusivo toma todas las salidas cuyas condiciones se cumplen")
        void inclusivo_tomaTodasLasVerdaderas() {
            DiagramaArmado armado = unDiagrama();
            armado.gateway(VENTAS, "Decide", TipoGateway.INCLUSIVO);
            armado.actividad(VENTAS, "Facturar", TipoActividad.USUARIO);
            armado.actividad(VENTAS, "Reservar", TipoActividad.USUARIO);
            armado.actividad(VENTAS, "Avisar", TipoActividad.USUARIO);
            armado.arco("Start", "Decide");
            armado.arcoCon("Decide", "Facturar", "order.total > 100", 1);
            armado.arcoCon("Decide", "Reservar", "order.vip == true", 2);
            armado.arcoCon("Decide", "Avisar", "order.total > 1000", 3);

            Caso caso = arrancar(armado, "{\"order\":{\"total\":150,\"vip\":true}}");

            assertThat(pasosDe(caso)).extracting(ActividadCaso::getNodoNombre)
                    .contains("Facturar", "Reservar").doesNotContain("Avisar");
        }

        private Caso unExclusivo(String variables, java.util.function.Consumer<DiagramaArmado> salidas) {
            DiagramaArmado armado = unDiagrama();
            armado.gateway(VENTAS, "Decide", TipoGateway.EXCLUSIVO);
            armado.actividad(VENTAS, "Aprobado", TipoActividad.USUARIO);
            armado.actividad(VENTAS, "Rechazado", TipoActividad.USUARIO);
            armado.arco("Start", "Decide");
            salidas.accept(armado);
            return arrancar(armado, variables);
        }
    }

    @Nested
    @DisplayName("Gateways que sincronizan")
    class Sincronizan {

        @Test
        @DisplayName("Un paralelo divergente pone un token en cada salida")
        void paralelo_abreTodasLasRamas() {
            Caso caso = arrancar(conForkYJoin(TipoGateway.PARALELO, TipoGateway.EXCLUSIVO));

            assertThat(pasosDe(caso)).extracting(ActividadCaso::getNodoNombre)
                    .contains("Izquierda", "Derecha");
        }

        @Test
        @DisplayName("Un join paralelo espera a que lleguen todos sus caminos")
        void joinParalelo_esperaATodos() {
            DiagramaArmado armado = conForkYJoin(TipoGateway.PARALELO, TipoGateway.PARALELO);
            Caso caso = arrancar(armado);
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());

            completarTarea(caso, grafo, "Izquierda");

            ActividadCaso join = pasoPor(caso, "Join");
            assertThat(join.getEstado()).isEqualTo(EstadoActividadCaso.EN_ESPERA);
            assertThat(join.getLlegadas()).isEqualTo(1);
            assertThat(pasosDe(caso)).extracting(ActividadCaso::getNodoNombre).doesNotContain("Done");
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ABIERTO);
        }

        @Test
        @DisplayName("Cuando llega el ultimo camino, el join paralelo sigue con un solo token")
        void joinParalelo_conTodos_sigue() {
            DiagramaArmado armado = conForkYJoin(TipoGateway.PARALELO, TipoGateway.PARALELO);
            Caso caso = arrancar(armado);
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());

            completarTarea(caso, grafo, "Izquierda");
            completarTarea(caso, grafo, "Derecha");

            ActividadCaso join = pasoPor(caso, "Join");
            assertThat(join.getLlegadas()).isEqualTo(2);
            assertThat(join.getEstado()).isEqualTo(EstadoActividadCaso.COMPLETADA);
            assertThat(pasosDe(caso)).filteredOn(paso -> paso.getNodoNombre().equals("Join")).hasSize(1);
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
        }

        @Test
        @DisplayName("Un join exclusivo no sincroniza: cada token que llega sigue de largo")
        void joinExclusivo_noSincroniza() {
            DiagramaArmado armado = conForkYJoin(TipoGateway.PARALELO, TipoGateway.EXCLUSIVO);
            Caso caso = arrancar(armado);
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());

            completarTarea(caso, grafo, "Izquierda");

            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ABIERTO);
            assertThat(pasosDe(caso)).filteredOn(paso -> paso.getNodoNombre().equals("Done")).hasSize(1);
        }

        @Test
        @DisplayName("Un join inclusivo espera mientras otro token vivo todavia pueda llegar hasta el")
        void joinInclusivo_esperaAlQuePuedeLlegar() {
            DiagramaArmado armado = conForkYJoin(TipoGateway.PARALELO, TipoGateway.INCLUSIVO);
            Caso caso = arrancar(armado);
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());

            completarTarea(caso, grafo, "Izquierda");

            assertThat(pasoPor(caso, "Join")).returns(EstadoActividadCaso.EN_ESPERA, ActividadCaso::getEstado);
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ABIERTO);
        }

        @Test
        @DisplayName("Cuando ya nadie puede llegar, el join inclusivo sigue sin esperar a mas")
        void joinInclusivo_sinPendientes_sigue() {
            DiagramaArmado armado = conForkYJoin(TipoGateway.PARALELO, TipoGateway.INCLUSIVO);
            Caso caso = arrancar(armado);
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());

            completarTarea(caso, grafo, "Izquierda");
            completarTarea(caso, grafo, "Derecha");

            assertThat(pasoPor(caso, "Join")).returns(EstadoActividadCaso.COMPLETADA, ActividadCaso::getEstado);
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
        }

        private DiagramaArmado conForkYJoin(TipoGateway fork, TipoGateway join) {
            DiagramaArmado armado = unDiagrama();
            armado.gateway(VENTAS, "Fork", fork);
            armado.actividad(VENTAS, "Izquierda", TipoActividad.USUARIO);
            armado.actividad(VENTAS, "Derecha", TipoActividad.USUARIO);
            armado.gateway(VENTAS, "Join", join);
            armado.evento(VENTAS, "Done", TipoEvento.FIN);
            armado.arco("Start", "Fork");
            armado.arco("Fork", "Izquierda");
            armado.arco("Fork", "Derecha");
            armado.arco("Izquierda", "Join");
            armado.arco("Derecha", "Join");
            armado.arco("Join", "Done");
            return armado;
        }
    }

    @Nested
    @DisplayName("Lo que el motor no ejecuta")
    class NoSeEjecuta {

        @Test
        @DisplayName("Los nodos de otro participante no son del caso, aunque esten dibujados por dentro")
        void otroPool_noSeEjecuta() {
            DiagramaArmado armado = unDiagrama();
            armado.pool("Carrier", TipoParticipante.PROVEEDOR, false, Integracion.TRANSPORTE);
            armado.lane("Carrier", "Dispatch");
            armado.actividad("Dispatch", "Plan the route", TipoActividad.USUARIO);
            armado.evento(VENTAS, "Done", TipoEvento.FIN);
            armado.arco("Start", "Done");

            Caso caso = arrancar(armado);

            assertThat(pasosDe(caso)).extracting(ActividadCaso::getNodoNombre).doesNotContain("Plan the route");
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
        }

        @Test
        @DisplayName("Un token en un nodo que no esta en la version no sigue: el caso queda en ERROR")
        void nodoQueNoEstaEnLaVersion_dejaElCasoEnError() {
            DiagramaArmado armado = unDiagrama();
            armado.evento(VENTAS, "Done", TipoEvento.FIN);
            armado.arco("Start", "Done");
            Caso caso = arrancarSinMotor(armado);
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
            // Un paso que apunta a un nodo que la version no tiene: el modelo vivo lo borro despues de publicar.
            actividadCasoRepository.saveAndFlush(ActividadCaso.builder().empresa(tienda).caso(caso).nodoId(9999L)
                    .nodoNombre("Fantasma").tipoNodo(TipoNodoCaso.ACTIVIDAD).subtipo("USUARIO")
                    .estado(EstadoActividadCaso.PENDIENTE).build());

            motor.avanzar(caso, grafo, null);

            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ERROR);
            assertThat(pasoPor(caso, "Fantasma")).returns(EstadoActividadCaso.FALLIDA, ActividadCaso::getEstado);
            assertThat(detallesDeLaBitacora(caso)).anyMatch(detalle -> detalle.contains("no esta en la version"));
        }

        @Test
        @DisplayName("Un ciclo que nunca espera se corta y deja el caso en ERROR en vez de colgar la peticion")
        void cicloSinEspera_dejaElCasoEnError() {
            DiagramaArmado armado = unDiagrama();
            armado.actividad(VENTAS, "Ida", TipoActividad.SERVICIO);
            armado.actividad(VENTAS, "Vuelta", TipoActividad.SERVICIO);
            armado.arco("Start", "Ida");
            armado.arco("Ida", "Vuelta");
            armado.arco("Vuelta", "Ida");

            Caso caso = arrancar(armado);

            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ERROR);
            assertThat(detallesDeLaBitacora(caso)).anyMatch(detalle -> detalle.contains("ciclo"));
        }
    }

    @Nested
    @DisplayName("Lo que el motor hace por encargo")
    class PorEncargo {

        @Test
        @DisplayName("Seguir desde un nodo pone los tokens de sus salidas y avanza el caso")
        void seguirDesde_sigueYAvanza() {
            DiagramaArmado armado = unDiagrama();
            armado.actividad(VENTAS, "Receive order", TipoActividad.USUARIO);
            armado.evento(VENTAS, "Done", TipoEvento.FIN);
            armado.arco("Start", "Receive order");
            armado.arco("Receive order", "Done");
            Caso caso = arrancar(armado);
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
            ActividadCaso tarea = pasoPor(caso, "Receive order");
            tarea.setEstado(EstadoActividadCaso.COMPLETADA);
            actividadCasoRepository.save(tarea);

            motor.seguirDesde(caso, grafo, tarea.getNodoId(), null);

            assertThat(pasoPor(caso, "Done")).returns(EstadoActividadCaso.COMPLETADA, ActividadCaso::getEstado);
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
        }

        @Test
        @DisplayName("Apagar los tokens deja omitido lo que seguia vivo y no toca lo ya hecho")
        void apagarTokens_omiteLoVivo() {
            DiagramaArmado armado = unDiagrama();
            armado.gateway(VENTAS, "Fork", TipoGateway.PARALELO);
            armado.actividad(VENTAS, "Izquierda", TipoActividad.USUARIO);
            armado.actividad(VENTAS, "Derecha", TipoActividad.USUARIO);
            armado.arco("Start", "Fork");
            armado.arco("Fork", "Izquierda");
            armado.arco("Fork", "Derecha");
            Caso caso = arrancar(armado);

            motor.apagarTokens(caso);
            em.flush();

            assertThat(pasoPor(caso, "Izquierda")).returns(EstadoActividadCaso.OMITIDA, ActividadCaso::getEstado);
            assertThat(pasoPor(caso, "Derecha")).returns(EstadoActividadCaso.OMITIDA, ActividadCaso::getEstado);
            assertThat(pasoPor(caso, "Start")).returns(EstadoActividadCaso.COMPLETADA, ActividadCaso::getEstado);
        }
    }

    /** Completa una tarea como lo hara el service: la marca completada y sigue por sus salidas. */
    private void completarTarea(Caso caso, GrafoDeVersion grafo, String nombre) {
        ActividadCaso tarea = pasoPor(caso, nombre);
        tarea.setEstado(EstadoActividadCaso.COMPLETADA);
        actividadCasoRepository.save(tarea);
        grafo.salidasDe(tarea.getNodoId())
                .forEach(salida -> grafo.nodo(salida.destinoId())
                        .ifPresent(destino -> motor.activar(caso, grafo, destino)));
        motor.avanzar(caso, grafo, null);
    }

    private Caso arrancar(DiagramaArmado armado) {
        return arrancar(armado, SIN_VARIABLES);
    }

    private Caso arrancar(DiagramaArmado armado, String variables) {
        GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
        Caso caso = arrancarSinMotor(armado, variables);
        motor.arrancar(caso, grafo, grafo.inicioAMano().orElseThrow(), null);
        em.flush();
        return caso;
    }

    /** Un caso recien abierto sin que el motor lo haya tocado, para poner a mano el token que hace falta. */
    private Caso arrancarSinMotor(DiagramaArmado armado) {
        return arrancarSinMotor(armado, SIN_VARIABLES);
    }

    private Caso arrancarSinMotor(DiagramaArmado armado, String variables) {
        return em.persistFlushFind(Caso.builder().empresa(tienda).proceso(proceso).versionProceso(version)
                .referencia("ORD-1").estado(EstadoCaso.ABIERTO).variables(variables).build());
    }

    /** El pool de la tienda con una lane de ventas y su evento de inicio, que es de donde parte todo. */
    private static DiagramaArmado unDiagrama() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS, VENDEDORES);
        armado.evento(VENTAS, "Start", TipoEvento.INICIO);
        return armado;
    }

    private List<ActividadCaso> pasosDe(Caso caso) {
        return actividadCasoRepository.findAllByCasoIdAndEmpresaIdOrderByIdAsc(caso.getId(), tienda.getId());
    }

    private ActividadCaso pasoPor(Caso caso, String nombre) {
        return pasosDe(caso).stream()
                .filter(paso -> paso.getNodoNombre().equals(nombre))
                .findFirst()
                .orElseThrow(() -> new AssertionError("El caso no paso por " + nombre));
    }

    private List<TipoEventoCaso> tiposDeLaBitacora(Caso caso) {
        return eventoCasoRepository.findAllByCasoIdAndEmpresaIdOrderByIdAsc(caso.getId(), tienda.getId()).stream()
                .map(EventoCaso::getTipo)
                .toList();
    }

    private List<String> detallesDeLaBitacora(Caso caso) {
        return eventoCasoRepository.findAllByCasoIdAndEmpresaIdOrderByIdAsc(caso.getId(), tienda.getId()).stream()
                .map(EventoCaso::getDetalle)
                .toList();
    }
}
