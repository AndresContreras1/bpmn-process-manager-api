package com.facimus.procesos.ejecucion.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.config.AuditoriaConfig;
import com.facimus.procesos.ejecucion.dto.response.PendientesPorSocioResponse;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.MensajeEntrante;
import com.facimus.procesos.ejecucion.model.MensajeSaliente;
import com.facimus.procesos.ejecucion.model.OrigenMensajeEntrante;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoDestino;

/**
 * Las dos bandejas: lo que el proceso mando y lo que le llego. Las consultas con las que la operacion las lee van
 * como consultas con nombre en la entidad (D22), asi que si alguna se renombra esto ni siquiera arranca.
 * <p>
 * El slice conserva la base del perfil test (replace = NONE), que es H2 con el esquema de Flyway: sin eso la
 * unicidad de la clave externa y los checks no se evaluarian.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditoriaConfig.class)
class MensajeriaRepositoryTest {

    private static final String HUELLA = "9f2c4a6b8d0e1f23456789abcdef0123456789abcdef0123456789abcdef0123";
    private static final String CUERPO = "{\"orderId\":\"ORD-1\"}";

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MensajeSalienteRepository mensajeSalienteRepository;

    @Autowired
    private MensajeEntranteRepository mensajeEntranteRepository;

    private Empresa tienda;
    private Proceso proceso;
    private Caso caso;

    @BeforeEach
    void crearLaTiendaSuProcesoYSuCaso() {
        tienda = empresa("Tienda de mensajes");
        proceso = proceso(tienda, "Order fulfillment");
        caso = caso(proceso, "ORD-1");
    }

    @Nested
    @DisplayName("La bandeja de salida")
    class Salida {

        @Test
        @DisplayName("Vencidos trae lo pendiente que ya tendria que haber llegado, en orden de vencimiento")
        void vencidos_soloLoPendienteYCumplido() {
            MensajeSaliente tarde = salienteRepositorio("Payment authorization request", 1, 2,
                    EstadoMensajeSaliente.PENDIENTE);
            MensajeSaliente justo = salienteRepositorio("Shipment request", 1, 5,
                    EstadoMensajeSaliente.PENDIENTE);
            salienteRepositorio("Order status notification", 1, 9, EstadoMensajeSaliente.PENDIENTE);
            salienteRepositorio("Ya entregado", 1, 2, EstadoMensajeSaliente.ENTREGADO);
            salienteRepositorio("Ya fallido", 1, 2, EstadoMensajeSaliente.FALLIDO);
            otraTiendaConSuSaliente();

            assertThat(mensajeSalienteRepository.vencidos(tienda.getId(), 5))
                    .extracting(MensajeSaliente::getId)
                    .containsExactly(tarde.getId(), justo.getId());
        }

        @Test
        @DisplayName("El panel cuenta lo pendiente por socio, sin mezclar tiendas")
        void pendientesPorSocio_agrupaYSeparaTiendas() {
            salienteRepositorio("Payment authorization request", 0, 1, EstadoMensajeSaliente.PENDIENTE);
            salienteRepositorio("Payment authorization request bis", 0, 1, EstadoMensajeSaliente.PENDIENTE);
            salienteRepositorio("Shipment request", 0, 1, EstadoMensajeSaliente.ENTREGADO);
            otraTiendaConSuSaliente();

            assertThat(mensajeSalienteRepository.pendientesPorSocio(tienda.getId()))
                    .extracting(PendientesPorSocioResponse::socio, PendientesPorSocioResponse::cantidad)
                    .containsExactly(tuple(Integracion.PAGOS, 2L));
            assertThat(mensajeSalienteRepository.countByEmpresaIdAndEstado(tienda.getId(),
                    EstadoMensajeSaliente.PENDIENTE)).isEqualTo(2);
        }

        @Test
        @DisplayName("La bandeja de un proceso se pagina y se puede pedir solo lo pendiente")
        void bandejaDeSalida_filtraPorEstadoYPorProceso() {
            MensajeSaliente pendiente = salienteRepositorio("Payment authorization request", 0, 1,
                    EstadoMensajeSaliente.PENDIENTE);
            salienteRepositorio("Shipment request", 0, 1, EstadoMensajeSaliente.ENTREGADO);
            Proceso otro = proceso(tienda, "Returns");
            em.persistAndFlush(saliente(caso(otro, "DEV-1"), "Refund request", 0, 1,
                    EstadoMensajeSaliente.PENDIENTE));

            assertThat(mensajeSalienteRepository.bandejaDeSalida(tienda.getId(), proceso.getId(), null,
                    Paginacion.de(0, 10))).hasSize(2);
            assertThat(mensajeSalienteRepository.bandejaDeSalida(tienda.getId(), proceso.getId(),
                    EstadoMensajeSaliente.PENDIENTE, Paginacion.de(0, 10)))
                    .extracting(MensajeSaliente::getId).containsExactly(pendiente.getId());
            assertThat(mensajeSalienteRepository.bandejaDeSalida(tienda.getId(), otro.getId(), null,
                    Paginacion.de(0, 10))).hasSize(1);
        }

        @Test
        @DisplayName("El cuerpo de un mensaje cabe mucho mas alla de lo que cabria en una columna corta")
        void cuerpo_guardaUnDocumentoLargo() {
            String largo = "{\"nota\":\"" + "x".repeat(6_000) + "\"}";
            MensajeSaliente guardado = mensajeSalienteRepository.save(MensajeSaliente.builder()
                    .empresa(tienda).caso(caso).mensajeId(1L).nombre("Payment authorization request")
                    .poolDestinoNombre("Payment gateway").integracion(Integracion.PAGOS)
                    .tipoDestino(TipoDestino.SERVICIO_WEB).clave("ORD-1").cuerpo(largo)
                    .estado(EstadoMensajeSaliente.PENDIENTE).tickCreacion(0).tickEntrega(1).intentos(0)
                    .fecha(LocalDateTime.now()).build());
            em.flush();
            em.clear();

            assertThat(mensajeSalienteRepository.findByIdAndEmpresaId(guardado.getId(), tienda.getId())
                    .orElseThrow().getCuerpo()).isEqualTo(largo);
        }

        @Test
        @DisplayName("La base no acepta un numero de intentos negativo")
        void intentos_noPuedenSerNegativos() {
            MensajeSaliente mensaje = salienteRepositorio("Shipment request", 0, 1,
                    EstadoMensajeSaliente.PENDIENTE);
            mensaje.setIntentos(-1);

            assertThatThrownBy(() -> mensajeSalienteRepository.saveAndFlush(mensaje))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        private MensajeSaliente salienteRepositorio(String nombre, int creacion, int entrega,
                EstadoMensajeSaliente estado) {
            MensajeSaliente guardado = mensajeSalienteRepository
                    .save(saliente(caso, nombre, creacion, entrega, estado));
            em.flush();
            return guardado;
        }
    }

    @Nested
    @DisplayName("La bandeja de entrada")
    class Entrada {

        @Test
        @DisplayName("La misma clave externa no entra dos veces en una tienda")
        void claveExterna_noSeRepiteEnLaMismaTienda() {
            entrante("Order placed", "ORD-1", "webhook-1", ResultadoCorrelacion.CASO_NUEVO);

            assertThatThrownBy(() -> mensajeEntranteRepository.saveAndFlush(MensajeEntrante.builder()
                    .empresa(tienda).proceso(proceso).nombre("Order placed").clave("ORD-2").cuerpo(CUERPO)
                    .origen(OrigenMensajeEntrante.MANUAL).claveExterna("webhook-1")
                    .resultado(ResultadoCorrelacion.CASO_NUEVO).tick(0).fecha(LocalDateTime.now()).build()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Dos tiendas pueden usar la misma clave externa sin saber la una de la otra")
        void claveExterna_deOtraTienda_noChoca() {
            entrante("Order placed", "ORD-1", "webhook-2", ResultadoCorrelacion.CASO_NUEVO);
            Empresa otra = empresa("Tienda vecina de mensajes");

            assertThatCode(() -> mensajeEntranteRepository.saveAndFlush(MensajeEntrante.builder().empresa(otra)
                    .proceso(proceso(otra, "Order fulfillment")).nombre("Order placed").clave("ORD-1")
                    .cuerpo(CUERPO).origen(OrigenMensajeEntrante.MANUAL).claveExterna("webhook-2")
                    .resultado(ResultadoCorrelacion.CASO_NUEVO).tick(0).fecha(LocalDateTime.now()).build()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Varios mensajes sin clave externa conviven: solo se exige que no se repita la que hay")
        void sinClaveExterna_noChocanEntreSi() {
            entrante("Payment authorization result", "ORD-1", null, ResultadoCorrelacion.ENTREGADO_A_CASO);

            assertThatCode(() -> entrante("Payment authorization result", "ORD-2", null,
                    ResultadoCorrelacion.ENTREGADO_A_CASO)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("El mensaje ya recibido se encuentra por su clave externa, y solo dentro de su tienda")
        void porClaveExterna_soloEnSuTienda() {
            MensajeEntrante guardado = entrante("Order placed", "ORD-1", "webhook-7",
                    ResultadoCorrelacion.CASO_NUEVO);
            Empresa otra = empresa("Tienda que no lo recibio");

            assertThat(mensajeEntranteRepository.findByEmpresaIdAndClaveExterna(tienda.getId(), "webhook-7"))
                    .map(MensajeEntrante::getId).contains(guardado.getId());
            assertThat(mensajeEntranteRepository.findByEmpresaIdAndClaveExterna(otra.getId(), "webhook-7"))
                    .isEmpty();
        }

        @Test
        @DisplayName("En espera trae los de toda la tienda que nadie ha recogido, y ninguno de otra")
        void enEsperaDeLaTienda_soloLosSinRecogerDeEsaTienda() {
            MensajeEntrante esperando = entrante("Payment authorization result", "ORD-9", null,
                    ResultadoCorrelacion.EN_ESPERA);
            MensajeEntrante deOtroProceso = otroProcesoConSuEntranteEnEspera();
            entrante("Payment authorization result", "ORD-1", null, ResultadoCorrelacion.ENTREGADO_A_CASO);
            otraTiendaConSuEntranteEnEspera();

            assertThat(mensajeEntranteRepository.pendientesDeLaTienda(tienda.getId(), 0))
                    .extracting(MensajeEntrante::getId)
                    .containsExactly(esperando.getId(), deOtroProceso.getId());
        }

        @Test
        @DisplayName("Lo que un socio dejo dicho para mas adelante no se mira hasta que el reloj llega a su tick")
        void loProgramado_noSeMiraAntesDeTiempo() {
            MensajeEntrante programado = mensajeEntranteRepository.save(MensajeEntrante.builder()
                    .empresa(tienda).proceso(proceso).nombre("Shipment confirmation").clave("ORD-1")
                    .cuerpo(CUERPO).origen(OrigenMensajeEntrante.SIMULADOR_TRANSPORTE)
                    .resultado(ResultadoCorrelacion.PROGRAMADO).tick(1).tickDisponible(4)
                    .fecha(LocalDateTime.now()).build());
            em.flush();

            assertThat(mensajeEntranteRepository.pendientesDeLaTienda(tienda.getId(), 3)).isEmpty();
            assertThat(mensajeEntranteRepository.pendientesDeLaTienda(tienda.getId(), 4))
                    .extracting(MensajeEntrante::getId).containsExactly(programado.getId());
        }

        @Test
        @DisplayName("El panel cuenta los que se quedaron esperando, sin contar los de otra tienda")
        void contarPorResultado_soloLosDeEsaTienda() {
            entrante("Payment authorization result", "ORD-9", null, ResultadoCorrelacion.EN_ESPERA);
            entrante("Payment authorization result", "ORD-1", null, ResultadoCorrelacion.ENTREGADO_A_CASO);
            otraTiendaConSuEntranteEnEspera();

            assertThat(mensajeEntranteRepository.countByEmpresaIdAndResultado(tienda.getId(),
                    ResultadoCorrelacion.EN_ESPERA)).isEqualTo(1);
        }

        private MensajeEntrante otroProcesoConSuEntranteEnEspera() {
            Proceso otro = proceso(tienda, "Returns");
            MensajeEntrante guardado = mensajeEntranteRepository.save(MensajeEntrante.builder()
                    .empresa(tienda).proceso(otro).nombre("Refund confirmed").clave("DEV-1").cuerpo(CUERPO)
                    .origen(OrigenMensajeEntrante.MANUAL).resultado(ResultadoCorrelacion.EN_ESPERA)
                    .tick(0).fecha(LocalDateTime.now()).build());
            em.flush();
            return guardado;
        }

        private void otraTiendaConSuEntranteEnEspera() {
            Empresa otra = empresa("Tienda que tambien espera");
            em.persistAndFlush(MensajeEntrante.builder().empresa(otra).proceso(proceso(otra, "Order fulfillment"))
                    .nombre("Payment authorization result").clave("ORD-9").cuerpo(CUERPO)
                    .origen(OrigenMensajeEntrante.MANUAL).resultado(ResultadoCorrelacion.EN_ESPERA)
                    .tick(0).fecha(LocalDateTime.now()).build());
        }

        @Test
        @DisplayName("La bandeja de un proceso se pagina y se puede pedir solo lo descartado")
        void bandejaDeEntrada_filtraPorResultado() {
            entrante("Order placed", "ORD-1", null, ResultadoCorrelacion.CASO_NUEVO);
            MensajeEntrante descartado = entrante("Payment authorization result", "ORD-404", null,
                    ResultadoCorrelacion.DESCARTADO);

            assertThat(mensajeEntranteRepository.bandejaDeEntrada(tienda.getId(), proceso.getId(), null,
                    Paginacion.de(0, 10))).hasSize(2);
            assertThat(mensajeEntranteRepository.bandejaDeEntrada(tienda.getId(), proceso.getId(),
                    ResultadoCorrelacion.DESCARTADO, Paginacion.de(0, 10)))
                    .extracting(MensajeEntrante::getId).containsExactly(descartado.getId());
        }

        private MensajeEntrante entrante(String nombre, String clave, String claveExterna,
                ResultadoCorrelacion resultado) {
            MensajeEntrante guardado = mensajeEntranteRepository.save(MensajeEntrante.builder()
                    .empresa(tienda).proceso(proceso).nombre(nombre).clave(clave).cuerpo(CUERPO)
                    .origen(OrigenMensajeEntrante.MANUAL).claveExterna(claveExterna).resultado(resultado)
                    .tick(0).fecha(LocalDateTime.now()).build());
            em.flush();
            return guardado;
        }
    }

    private MensajeSaliente saliente(Caso suCaso, String nombre, int creacion, int entrega,
            EstadoMensajeSaliente estado) {
        return MensajeSaliente.builder()
                .empresa(tienda).caso(suCaso).mensajeId((long) nombre.hashCode()).nombre(nombre)
                .poolDestinoNombre("Payment gateway").integracion(Integracion.PAGOS)
                .tipoDestino(TipoDestino.SERVICIO_WEB).clave(suCaso.getReferencia()).cuerpo(CUERPO)
                .estado(estado).tickCreacion(creacion).tickEntrega(entrega).intentos(0)
                .fecha(LocalDateTime.now()).build();
    }

    private void otraTiendaConSuSaliente() {
        Empresa otra = empresa("Tienda con su propia bandeja");
        Proceso suyo = proceso(otra, "Order fulfillment");
        em.persistAndFlush(MensajeSaliente.builder()
                .empresa(otra).caso(caso(suyo, "ORD-1")).mensajeId(1L).nombre("Payment authorization request")
                .poolDestinoNombre("Payment gateway").integracion(Integracion.PAGOS)
                .tipoDestino(TipoDestino.SERVICIO_WEB).clave("ORD-1").cuerpo(CUERPO)
                .estado(EstadoMensajeSaliente.PENDIENTE).tickCreacion(0).tickEntrega(1).intentos(0)
                .fecha(LocalDateTime.now()).build());
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
                .empresa(duena).nombre(nombre).descripcion("Checkout to delivery").categoria("Fulfillment")
                .estado(EstadoProceso.PUBLICADO).activo(true).build());
    }

    private Caso caso(Proceso suProceso, String referencia) {
        VersionProceso version = em.persistFlushFind(VersionProceso.builder()
                .empresa(suProceso.getEmpresa()).proceso(suProceso).numero(1).estado(EstadoVersion.VIGENTE)
                .fechaPublicacion(LocalDateTime.now()).huella(HUELLA).definicion("{}").build());
        return em.persistFlushFind(Caso.builder()
                .empresa(suProceso.getEmpresa()).proceso(suProceso).versionProceso(version)
                .referencia(referencia).estado(EstadoCaso.ABIERTO).variables("{}").build());
    }
}
