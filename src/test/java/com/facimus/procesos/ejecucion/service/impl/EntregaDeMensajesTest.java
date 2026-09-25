package com.facimus.procesos.ejecucion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.ejecucion.TiendaConMensajeria;
import com.facimus.procesos.ejecucion.dto.response.MensajeEntranteResponse;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.MensajeSaliente;
import com.facimus.procesos.ejecucion.repository.MensajeSalienteRepository;
import com.facimus.procesos.ejecucion.service.CasoService;
import com.facimus.procesos.ejecucion.service.DatosDelEntrante;
import com.facimus.procesos.ejecucion.service.MensajeriaService;
import com.facimus.procesos.ejecucion.service.TareaService;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.EmpresaService;

/**
 * Entregar un mensaje dos veces seria contestarle dos veces al caso. El estado del saliente se vuelve a mirar con
 * el caso ya bloqueado, no antes: entre que el tick lo elige y le llega el turno, otro hilo pudo haberlo
 * entregado. Aqui se le pide la entrega dos veces a proposito, que es lo que un segundo hilo haria.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntregaDeMensajesTest {

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ConfiguracionTiendaService configuracionTiendaService;

    @Autowired
    private CasoService casoService;

    @Autowired
    private TareaService tareaService;

    @Autowired
    private MensajeriaService mensajeriaService;

    @Autowired
    private MensajeSalienteRepository mensajeSalienteRepository;

    @Autowired
    private EntregaDeMensajes entrega;

    @Autowired
    private ApplicationContext contexto;

    private TiendaConMensajeria tienda;
    private Long empresaId;
    private Long adminId;

    @BeforeAll
    void registrarLaTienda() {
        tienda = new TiendaConMensajeria(contexto);
        empresaId = empresaService.registrar("Tienda que entrega", "900111777-1", "contacto@entrega.com",
                "Administradora", "admin@entrega.com", "clave12345").id();
        adminId = usuarioRepository.findByEmail("admin@entrega.com").orElseThrow().getId();
    }

    @Test
    @DisplayName("Pedir la entrega del mismo mensaje dos veces no le contesta dos veces al caso")
    void entregarDosVeces_soloContestaUnaVez() {
        Long procesoId = tienda.publicar(empresaId, adminId, "Order fulfillment delivered twice");
        Long casoId = unPedidoEsperandoLaPasarela(procesoId, "ORD-3001");
        Long salienteId = mensajeSalienteRepository
                .findAllByCasoIdAndEmpresaIdOrderByIdAsc(casoId, empresaId).getFirst().getId();
        int tick = configuracionTiendaService.avanzarReloj(empresaId, 1);

        entrega.entregar(empresaId, salienteId, tick);
        entrega.entregar(empresaId, salienteId, tick);

        assertThat(mensajeriaService.entrantesDelCaso(empresaId, casoId))
                .extracting(MensajeEntranteResponse::nombre)
                .containsExactly(TiendaConMensajeria.PEDIDO, TiendaConMensajeria.RESULTADO);
        assertThat(mensajeSalienteRepository.findByIdAndEmpresaId(salienteId, empresaId).orElseThrow())
                .returns(EstadoMensajeSaliente.ENTREGADO, MensajeSaliente::getEstado)
                .returns(1, MensajeSaliente::getIntentos);
        assertThat(casoService.obtener(empresaId, casoId).caso().estado()).isEqualTo(EstadoCaso.TERMINADO);
    }

    private Long unPedidoEsperandoLaPasarela(Long procesoId, String referencia) {
        Long casoId = mensajeriaService.recibir(empresaId, procesoId, DatosDelEntrante.aMano(
                TiendaConMensajeria.PEDIDO, null, Map.of("orderId", referencia), null)).casoId();
        TareaResponse tarea = tareaService.bandeja(empresaId, adminId, false, null, procesoId, null,
                        Paginacion.de(0, 10)).content().stream()
                .filter(pendiente -> pendiente.casoId().equals(casoId))
                .findFirst().orElseThrow();
        tareaService.completar(empresaId, adminId, tarea.id(), null);
        return casoId;
    }
}
