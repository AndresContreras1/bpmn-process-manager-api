package com.facimus.procesos.modelado.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.config.AuditoriaConfig;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.AccionSiFalla;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoDeDato;
import com.facimus.procesos.modelado.model.TipoDestino;
import com.facimus.procesos.modelado.model.TipoEvento;

/**
 * El mensaje anclado visto desde la base: las llaves hacia los nodos y hacia el mensaje que lo responde, los campos
 * guardados como JSON en su propia columna, y el check que no deja desviar el flujo sin decir a donde.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditoriaConfig.class)
class MensajeRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MensajeRepository mensajeRepository;

    private DiagramaDePrueba tienda;
    private Proceso proceso;
    private Pool pool;
    private Pool pasarela;
    private Lane ventas;

    @BeforeEach
    void armarUnDiagramaMinimo() {
        tienda = new DiagramaDePrueba(em, "Tienda de mensajes");
        proceso = tienda.proceso("Order fulfillment");
        pool = tienda.pool(proceso, "Demo Store", 0);
        pasarela = tienda.pool(proceso, "Payment gateway", 1);
        ventas = tienda.lane(pool, tienda.rol("Sales"), "Sales", 0);
    }

    @Test
    @DisplayName("El mensaje guarda sus anclajes, su respuesta y sus campos, y los devuelve igual al leerlos")
    void mensajeAnclado_seGuardaYSeLeeCompleto() {
        Actividad enviar = tienda.actividad(ventas, "Request payment authorization", TipoActividad.ENVIO);
        Evento esperar = tienda.evento(ventas, "Payment result received", TipoEvento.MENSAJE_INTERMEDIO);
        Mensaje resultado = em.persistFlushFind(Mensaje.builder()
                .empresa(tienda.empresa())
                .proceso(proceso)
                .nombre("Payment authorization result")
                .contenido("Approved or declined")
                .poolOrigen(pasarela)
                .poolDestino(pool)
                .nodoDestino(esperar)
                .variable("payment")
                .campos(List.of(new CampoDeMensaje("status", TipoDeDato.TEXTO),
                        new CampoDeMensaje("transactionId", TipoDeDato.TEXTO)))
                .build());

        Mensaje peticion = em.persistFlushFind(Mensaje.builder()
                .empresa(tienda.empresa())
                .proceso(proceso)
                .nombre("Payment authorization request")
                .contenido("Order total and tokenized card")
                .poolOrigen(pool)
                .poolDestino(pasarela)
                .nodoOrigen(enviar)
                .tipoDestino(TipoDestino.SERVICIO_WEB)
                .respuestaEsperada(resultado)
                .campos(List.of(new CampoDeMensaje("total", TipoDeDato.NUMERO)))
                .build());
        em.clear();

        Mensaje leido = mensajeRepository.findByIdAndEmpresaId(peticion.getId(), tienda.empresa().getId())
                .orElseThrow();
        assertThat(leido.getNodoOrigen().getId()).isEqualTo(enviar.getId());
        assertThat(leido.getRespuestaEsperada().getNombre()).isEqualTo("Payment authorization result");
        assertThat(leido.getTipoDestino()).isEqualTo(TipoDestino.SERVICIO_WEB);
        // Sin mandar nada, el envio sigue su camino si falla.
        assertThat(leido.getSiFalla()).isEqualTo(AccionSiFalla.CONTINUAR);
        assertThat(leido.isOrigenExterno()).isFalse();
        assertThat(leido.getCampos()).extracting(CampoDeMensaje::nombre, CampoDeMensaje::tipo)
                .containsExactly(tuple("total", TipoDeDato.NUMERO));
        assertThat(mensajeRepository.findByIdAndEmpresaId(resultado.getId(), tienda.empresa().getId())
                .orElseThrow().getCampos())
                .extracting(CampoDeMensaje::nombre)
                .containsExactly("status", "transactionId");
    }

    @Test
    @DisplayName("La base no deja desviar el flujo sin decir a que actividad")
    void manejoDeErrorSinActividad_laBaseLoRechaza() {
        Mensaje sinActividad = Mensaje.builder()
                .empresa(tienda.empresa())
                .proceso(proceso)
                .nombre("Payment authorization request")
                .contenido("Order total")
                .poolOrigen(pool)
                .poolDestino(pasarela)
                .siFalla(AccionSiFalla.MANEJAR_ERROR)
                .build();

        assertThatThrownBy(() -> mensajeRepository.saveAndFlush(sinActividad))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Un mensaje no puede quedar guardado como su propia respuesta")
    void mensajeQueSeRespondeASiMismo_laBaseLoRechaza() {
        Mensaje mensaje = em.persistFlushFind(Mensaje.builder()
                .empresa(tienda.empresa())
                .proceso(proceso)
                .nombre("Shipment request")
                .contenido("Package size")
                .poolOrigen(pool)
                .poolDestino(pasarela)
                .build());

        mensaje.setRespuestaEsperada(mensaje);

        assertThatThrownBy(() -> mensajeRepository.saveAndFlush(mensaje))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
