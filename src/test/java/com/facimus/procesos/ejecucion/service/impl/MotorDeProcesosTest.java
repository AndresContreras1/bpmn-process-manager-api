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
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.EventoCaso;
import com.facimus.procesos.ejecucion.model.MensajeSaliente;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.model.TipoNodoCaso;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.EventoCasoRepository;
import com.facimus.procesos.ejecucion.repository.MensajeSalienteRepository;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.modelado.model.AccionSiFalla;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.PoliticaSinCaso;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoDeDato;
import com.facimus.procesos.modelado.model.TipoDestino;
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
    private static final String PASARELA = "Payment gateway";
    private static final String SIN_VARIABLES = "{}";
    /**
     * El tick en que corre cada prueba. El motor no lee el reloj: recibe el momento, asi que aqui es un numero y no
     * una tienda entera con su configuracion.
     */
    private static final int TICK = 3;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ActividadCasoRepository actividadCasoRepository;

    @Autowired
    private EventoCasoRepository eventoCasoRepository;

    @Autowired
    private MensajeSalienteRepository mensajeSalienteRepository;

    private MotorDeProcesos motor;
    private Empresa tienda;
    private Proceso proceso;
    private VersionProceso version;

    @BeforeEach
    void armarElMotorYLaTienda() {
        JsonMapper json = JsonMapper.builder().build();
        Bitacora bitacora = new Bitacora(eventoCasoRepository);
        motor = new MotorDeProcesos(actividadCasoRepository,
                new BandejaDeSalida(mensajeSalienteRepository, bitacora, json), bitacora, json);
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
        @DisplayName("Un evento de mensaje intermedio sin mensaje anclado no tiene nada que esperar y pasa")
        void mensajeIntermedioSinMensaje_pasaDeLargo() {
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
        @DisplayName("Un mensaje de fin sin mensaje anclado cierra su camino igual que un fin normal")
        void mensajeFinSinMensaje_cierraSuCamino() {
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
        @DisplayName("Una actividad de envio sin mensaje anclado no tiene a quien mandarle nada y sigue de largo")
        void envioSinMensaje_sigueDeLargo() {
            assertThat(unaActividadDeTipo(TipoActividad.ENVIO)).isEqualTo(EstadoCaso.TERMINADO);
        }

        @Test
        @DisplayName("Una actividad de recepcion sin mensaje anclado sigue de largo en vez de esperar para siempre")
        void recepcionSinMensaje_sigueDeLargo() {
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

            motor.avanzar(caso, grafo, Momento.en(TICK));

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

            motor.seguirDesde(caso, grafo, tarea.getNodoId(), Momento.en(TICK));

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

            motor.apagarTokens(caso, Momento.en(TICK));
            em.flush();

            assertThat(pasoPor(caso, "Izquierda")).returns(EstadoActividadCaso.OMITIDA, ActividadCaso::getEstado);
            assertThat(pasoPor(caso, "Derecha")).returns(EstadoActividadCaso.OMITIDA, ActividadCaso::getEstado);
            assertThat(pasoPor(caso, "Start")).returns(EstadoActividadCaso.COMPLETADA, ActividadCaso::getEstado);
        }
    }

    @Nested
    @DisplayName("Mensajes que salen y que se esperan")
    class Mensajes {

        @Test
        @DisplayName("Una actividad de envio escribe su mensaje en la bandeja con los campos que declara y sigue")
        void envio_poneElMensajeEnLaBandeja() {
            DiagramaArmado armado = conPasarela();
            armado.actividad(VENTAS, "Request payment authorization", TipoActividad.ENVIO);
            armado.evento(VENTAS, "Done", TipoEvento.FIN);
            armado.arco("Start", "Request payment authorization");
            armado.arco("Request payment authorization", "Done");
            armado.mensaje("Payment authorization request", TIENDA, PASARELA,
                    "Request payment authorization", null, TipoDestino.SERVICIO_WEB, AccionSiFalla.CONTINUAR,
                    null, false);
            armado.datosDelMensaje("Payment authorization request", "payment",
                    new CampoDeMensaje("orderId", TipoDeDato.TEXTO),
                    new CampoDeMensaje("total", TipoDeDato.NUMERO));
            armado.correlacion("Payment authorization request", PoliticaSinCaso.DESCARTAR);

            Caso caso = arrancar(armado, "{\"orderId\":\"ORD-77\",\"total\":150}");

            MensajeSaliente saliente = salientesDe(caso).getFirst();
            assertThat(saliente.getNombre()).isEqualTo("Payment authorization request");
            assertThat(saliente.getPoolDestinoNombre()).isEqualTo(PASARELA);
            assertThat(saliente.getIntegracion()).isEqualTo(Integracion.PAGOS);
            assertThat(saliente.getEstado()).isEqualTo(EstadoMensajeSaliente.PENDIENTE);
            assertThat(saliente.getCuerpo()).isEqualTo("{\"orderId\":\"ORD-77\",\"total\":150}");
            assertThat(saliente.getClave()).isEqualTo("ORD-77");
            assertThat(saliente.getTickCreacion()).isEqualTo(TICK);
            assertThat(saliente.getTickEntrega()).isEqualTo(TICK + 1);
            assertThat(pasoPor(caso, "Request payment authorization"))
                    .returns(EstadoActividadCaso.COMPLETADA, ActividadCaso::getEstado);
            assertThat(tiposDeLaBitacora(caso)).contains(TipoEventoCaso.MENSAJE_ENVIADO);
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
        }

        @Test
        @DisplayName("Un campo que el caso no tiene viaja vacio y queda anotado en la linea de tiempo")
        void campoAusente_viajaVacioYQuedaAnotado() {
            Caso caso = arrancar(conEnvioSimple(AccionSiFalla.CONTINUAR), SIN_VARIABLES);

            assertThat(salientesDe(caso).getFirst().getCuerpo()).isEqualTo("{\"total\":null}");
            assertThat(tiposDeLaBitacora(caso)).contains(TipoEventoCaso.VARIABLE_AUSENTE);
            assertThat(detallesDeLaBitacora(caso)).anyMatch(detalle -> detalle.contains("viaja vacio"));
        }

        @Test
        @DisplayName("Un campo con puntos baja por las variables del caso y viaja con su ultimo tramo")
        void campoConPuntos_viajaConSuUltimoTramo() {
            DiagramaArmado armado = conEnvioSimple(AccionSiFalla.CONTINUAR);
            armado.datosDelMensaje("Payment authorization request", "payment",
                    new CampoDeMensaje("order.total", TipoDeDato.NUMERO));

            Caso caso = arrancar(armado, "{\"order\":{\"total\":150}}");

            // Dentro del caso el dato se llama order.total; el socio del otro lado lo conoce como total.
            assertThat(salientesDe(caso).getFirst().getCuerpo()).isEqualTo("{\"total\":150}");
            assertThat(tiposDeLaBitacora(caso)).doesNotContain(TipoEventoCaso.VARIABLE_AUSENTE);
        }

        @Test
        @DisplayName("Sin campo de correlacion, la clave del mensaje es la referencia del caso")
        void sinCampoDeCorrelacion_laClaveEsLaReferencia() {
            DiagramaArmado armado = conEnvioSimple(AccionSiFalla.CONTINUAR);
            armado.quitarCorrelacionDe("Payment authorization request");

            Caso caso = arrancar(armado, SIN_VARIABLES);

            assertThat(salientesDe(caso).getFirst().getClave()).isEqualTo("ORD-1");
        }

        @Test
        @DisplayName("Una actividad de servicio con mensaje anclado tambien lo manda")
        void servicioConMensaje_tambienManda() {
            DiagramaArmado armado = conPasarela();
            armado.actividad(VENTAS, "Notify the customer", TipoActividad.SERVICIO);
            armado.evento(VENTAS, "Done", TipoEvento.FIN);
            armado.arco("Start", "Notify the customer");
            armado.arco("Notify the customer", "Done");
            armado.mensaje("Order status notification", TIENDA, PASARELA, "Notify the customer", null,
                    TipoDestino.CORREO, AccionSiFalla.CONTINUAR, null, false);

            Caso caso = arrancar(armado);

            assertThat(salientesDe(caso)).extracting(MensajeSaliente::getNombre)
                    .containsExactly("Order status notification");
        }

        @Test
        @DisplayName("Una actividad de recepcion se queda esperando su mensaje, y el caso no termina")
        void recepcionConMensaje_seQuedaEsperando() {
            DiagramaArmado armado = conPasarela();
            armado.actividad(VENTAS, "Wait for the payment", TipoActividad.RECEPCION);
            armado.evento(VENTAS, "Done", TipoEvento.FIN);
            armado.arco("Start", "Wait for the payment");
            armado.arco("Wait for the payment", "Done");
            armado.mensaje("Payment authorization result", PASARELA, TIENDA, null, "Wait for the payment",
                    null, AccionSiFalla.CONTINUAR, null, false);

            Caso caso = arrancar(armado);

            assertThat(pasoPor(caso, "Wait for the payment"))
                    .returns(EstadoActividadCaso.EN_ESPERA, ActividadCaso::getEstado);
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ABIERTO);
            assertThat(detallesDeLaBitacora(caso))
                    .anyMatch(detalle -> detalle.contains("espera el mensaje"));
        }

        @Test
        @DisplayName("Un evento de mensaje en mitad del flujo se queda esperando; el de inicio no")
        void mensajeIntermedio_esperaYElDeInicioNo() {
            DiagramaArmado armado = DiagramaArmado.reciennacido();
            armado.pool(PASARELA, TipoParticipante.SISTEMA_EXTERNO, true, Integracion.PAGOS);
            armado.lane(TIENDA, VENTAS, VENDEDORES);
            armado.evento(VENTAS, "Order received", TipoEvento.MENSAJE_INICIO);
            armado.evento(VENTAS, "Payment result received", TipoEvento.MENSAJE_INTERMEDIO);
            armado.evento(VENTAS, "Done", TipoEvento.FIN);
            armado.arco("Order received", "Payment result received");
            armado.arco("Payment result received", "Done");
            armado.mensaje("Order placed", PASARELA, TIENDA, null, "Order received", null,
                    AccionSiFalla.CONTINUAR, null, true);
            armado.mensaje("Payment authorization result", PASARELA, TIENDA, null, "Payment result received",
                    null, AccionSiFalla.CONTINUAR, null, false);
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
            Caso caso = arrancarSinMotor(armado);

            // Al inicio por mensaje el token llega porque el mensaje ya llego: no vuelve a esperarlo.
            motor.arrancar(caso, grafo, grafo.inicioPorMensaje().orElseThrow(), Momento.en(TICK));
            em.flush();

            assertThat(pasoPor(caso, "Order received"))
                    .returns(EstadoActividadCaso.COMPLETADA, ActividadCaso::getEstado);
            assertThat(pasoPor(caso, "Payment result received"))
                    .returns(EstadoActividadCaso.EN_ESPERA, ActividadCaso::getEstado);
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ABIERTO);
        }

        @Test
        @DisplayName("Un fin de mensaje manda el suyo y ahi mismo termina el camino")
        void mensajeFin_mandaYTermina() {
            DiagramaArmado armado = conPasarela();
            armado.evento(VENTAS, "Order confirmed", TipoEvento.MENSAJE_FIN);
            armado.arco("Start", "Order confirmed");
            armado.mensaje("Order confirmation", TIENDA, PASARELA, "Order confirmed", null, TipoDestino.CORREO,
                    AccionSiFalla.CONTINUAR, null, false);

            Caso caso = arrancar(armado);

            assertThat(salientesDe(caso)).extracting(MensajeSaliente::getNombre)
                    .containsExactly("Order confirmation");
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
            assertThat(tiposDeLaBitacora(caso)).contains(TipoEventoCaso.MENSAJE_ENVIADO,
                    TipoEventoCaso.CASO_TERMINADO);
        }

        @Test
        @DisplayName("Cuando llega el mensaje que se esperaba, el token se completa y el caso sigue")
        void mensajeRecibido_completaElTokenYSigue() {
            DiagramaArmado armado = conPasarela();
            armado.actividad(VENTAS, "Wait for the payment", TipoActividad.RECEPCION);
            armado.evento(VENTAS, "Done", TipoEvento.FIN);
            armado.arco("Start", "Wait for the payment");
            armado.arco("Wait for the payment", "Done");
            armado.mensaje("Payment authorization result", PASARELA, TIENDA, null, "Wait for the payment", null,
                    AccionSiFalla.CONTINUAR, null, false);
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
            Caso caso = arrancar(armado);

            motor.mensajeRecibido(caso, grafo, pasoPor(caso, "Wait for the payment"), Momento.en(TICK + 2));
            em.flush();

            assertThat(pasoPor(caso, "Wait for the payment"))
                    .returns(EstadoActividadCaso.COMPLETADA, ActividadCaso::getEstado)
                    .returns(TICK + 2, ActividadCaso::getTickFin);
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
        }

        @Test
        @DisplayName("Un envio que falla y dice CONTINUAR deja la linea y el caso sigue donde estaba")
        void envioFallido_continuar_soloLoAnota() {
            DiagramaArmado armado = conEnvioSimple(AccionSiFalla.CONTINUAR);
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
            Caso caso = arrancar(armado);

            motor.envioFallido(caso, grafo, grafo.mensajePorNombre("Payment authorization request").orElseThrow(),
                    "la pasarela no contesto", Momento.en(TICK + 1));
            em.flush();

            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ABIERTO);
            assertThat(pasoPor(caso, "Wait for the payment"))
                    .returns(EstadoActividadCaso.EN_ESPERA, ActividadCaso::getEstado);
            assertThat(tiposDeLaBitacora(caso)).contains(TipoEventoCaso.ENVIO_FALLIDO);
            assertThat(detallesDeLaBitacora(caso)).anyMatch(detalle -> detalle.contains("sigue por donde iba"));
            assertThat(pasosDe(caso)).extracting(ActividadCaso::getNodoNombre).doesNotContain("Cancel order");
        }

        @Test
        @DisplayName("Un envio que falla y dice MANEJAR_ERROR activa la actividad que lo atiende")
        void envioFallido_manejarError_activaLaActividad() {
            DiagramaArmado armado = conEnvioSimple(AccionSiFalla.MANEJAR_ERROR);
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
            Caso caso = arrancar(armado);

            motor.envioFallido(caso, grafo, grafo.mensajePorNombre("Payment authorization request").orElseThrow(),
                    "la pasarela no contesto", Momento.en(TICK + 1));
            em.flush();

            assertThat(pasoPor(caso, "Cancel order"))
                    .returns(EstadoActividadCaso.EN_ESPERA, ActividadCaso::getEstado)
                    .returns(TICK + 1, ActividadCaso::getTickInicio);
            assertThat(detallesDeLaBitacora(caso))
                    .anyMatch(detalle -> detalle.contains("el caso pasa por \"Cancel order\""));
        }

        @Test
        @DisplayName("Si la actividad que atiende el fallo no esta en la version, el caso queda en ERROR")
        void envioFallido_sinActividadEnLaVersion_dejaElCasoEnError() {
            DiagramaArmado armado = conEnvioSimple(AccionSiFalla.MANEJAR_ERROR);
            // La actividad que atenderia el fallo esta en el pool del socio, asi que el caso no puede ir a ella.
            armado.dejaDeSerCajaNegra(PASARELA);
            armado.lane(PASARELA, "Risk");
            armado.actividad("Risk", "Review by hand", TipoActividad.USUARIO);
            armado.manejaElError("Payment authorization request", "Review by hand");
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
            Caso caso = arrancar(armado);

            motor.envioFallido(caso, grafo, grafo.mensajePorNombre("Payment authorization request").orElseThrow(),
                    "la pasarela no contesto", Momento.en(TICK + 1));
            em.flush();

            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ERROR);
            assertThat(detallesDeLaBitacora(caso))
                    .anyMatch(detalle -> detalle.contains("no esta en la version del caso"));
        }

        @Test
        @DisplayName("Un envio que falla y dice FINALIZAR apaga los tokens y deja el caso fallido")
        void envioFallido_finalizar_dejaElCasoFallido() {
            DiagramaArmado armado = conEnvioSimple(AccionSiFalla.FINALIZAR);
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
            Caso caso = arrancar(armado);

            motor.envioFallido(caso, grafo, grafo.mensajePorNombre("Payment authorization request").orElseThrow(),
                    "la pasarela no contesto", Momento.en(TICK + 4));
            em.flush();

            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.FALLIDO);
            assertThat(caso.getTickFin()).isEqualTo(TICK + 4);
            assertThat(caso.getFechaFin()).isNotNull();
            assertThat(pasoPor(caso, "Wait for the payment"))
                    .returns(EstadoActividadCaso.OMITIDA, ActividadCaso::getEstado)
                    .returns(TICK + 4, ActividadCaso::getTickFin);
            assertThat(detallesDeLaBitacora(caso)).anyMatch(detalle -> detalle.contains("queda fallido"));
        }

        /**
         * Un envio que puede fallar y un caso que sigue vivo esperando la respuesta, que es la situacion real: el
         * fallo llega despues, cuando el caso todavia esta en pie. Asi se ve lo que hace cada siFalla sobre el."
         */
        private DiagramaArmado conEnvioSimple(AccionSiFalla siFalla) {
            DiagramaArmado armado = conPasarela();
            armado.actividad(VENTAS, "Request payment authorization", TipoActividad.ENVIO);
            armado.actividad(VENTAS, "Wait for the payment", TipoActividad.USUARIO);
            armado.actividad(VENTAS, "Cancel order", TipoActividad.USUARIO);
            armado.evento(VENTAS, "Done", TipoEvento.FIN);
            armado.arco("Start", "Request payment authorization");
            armado.arco("Request payment authorization", "Wait for the payment");
            armado.arco("Wait for the payment", "Done");
            armado.arco("Cancel order", "Done");
            armado.mensaje("Payment authorization request", TIENDA, PASARELA, "Request payment authorization",
                    null, TipoDestino.SERVICIO_WEB, siFalla, "Cancel order", false);
            armado.datosDelMensaje("Payment authorization request", "payment",
                    new CampoDeMensaje("total", TipoDeDato.NUMERO));
            armado.correlacion("Payment authorization request", PoliticaSinCaso.DESCARTAR);
            return armado;
        }

        private DiagramaArmado conPasarela() {
            DiagramaArmado armado = DiagramaArmado.reciennacido();
            armado.pool(PASARELA, TipoParticipante.SISTEMA_EXTERNO, true, Integracion.PAGOS);
            armado.lane(TIENDA, VENTAS, VENDEDORES);
            armado.evento(VENTAS, "Start", TipoEvento.INICIO);
            return armado;
        }

        private List<MensajeSaliente> salientesDe(Caso caso) {
            return mensajeSalienteRepository.findAllByCasoIdAndEmpresaIdOrderByIdAsc(caso.getId(), tienda.getId());
        }
    }

    /** Completa una tarea como lo hara el service: la marca completada y sigue por sus salidas. */
    private void completarTarea(Caso caso, GrafoDeVersion grafo, String nombre) {
        ActividadCaso tarea = pasoPor(caso, nombre);
        tarea.setEstado(EstadoActividadCaso.COMPLETADA);
        actividadCasoRepository.save(tarea);
        grafo.salidasDe(tarea.getNodoId())
                .forEach(salida -> grafo.nodo(salida.destinoId())
                        .ifPresent(destino -> motor.activar(caso, grafo, destino, Momento.en(TICK))));
        motor.avanzar(caso, grafo, Momento.en(TICK));
    }

    private Caso arrancar(DiagramaArmado armado) {
        return arrancar(armado, SIN_VARIABLES);
    }

    private Caso arrancar(DiagramaArmado armado, String variables) {
        GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
        Caso caso = arrancarSinMotor(armado, variables);
        motor.arrancar(caso, grafo, grafo.inicioAMano().orElseThrow(), Momento.en(TICK));
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
