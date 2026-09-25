package com.facimus.procesos.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.ejecucion.dto.response.CasoDetalleResponse;
import com.facimus.procesos.ejecucion.dto.response.CasoResponse;
import com.facimus.procesos.ejecucion.dto.response.PasoDelCasoResponse;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.service.CasoService;
import com.facimus.procesos.ejecucion.service.SimulacionService;
import com.facimus.procesos.ejecucion.service.TareaService;
import com.facimus.procesos.gestion.dto.response.ConfiguracionTiendaResponse;
import com.facimus.procesos.gestion.model.ParametrosSimulacion;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.EmpresaService;

/**
 * La tanda entera: veinte pedidos del cliente simulado, dos personas que atienden sus bandejas y unos cuantos
 * ticks. Con la tasa de rechazo en cero terminan los veinte enviados; con la tasa en cien, los veinte cancelados
 * y ninguno llega a bodega. Es la demo que el PR promete, contada en una prueba.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VeintePedidosTest {

    private static final int PEDIDOS = 20;

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
    private SimulacionService simulacionService;

    @Autowired
    private ApplicationContext contexto;

    private TiendaConMensajeria demo;
    private Long empresaId;
    private Long adminId;

    @BeforeAll
    void registrarLaTienda() {
        demo = new TiendaConMensajeria(contexto);
        empresaId = empresaService.registrar("Tienda de la tanda", "900909090-1", "contacto@tanda.com",
                "Administradora", "admin@tanda.com", "clave12345").id();
        adminId = usuarioRepository.findByEmail("admin@tanda.com").orElseThrow().getId();
    }

    @Test
    @DisplayName("Veinte pedidos aprobados terminan los veinte enviados, con dos personas y cuatro ticks")
    void veintePedidosAprobados_terminanEnviados() {
        conRechazoDePagos(0);
        Long procesoId = demo.publicarLaDemo(empresaId, adminId, "Order fulfillment for twenty orders");

        simulacionService.pedidos(empresaId, procesoId, PEDIDOS, null);
        completarLaBandeja(procesoId, TiendaConMensajeria.REVISAR);
        simulacionService.tick(empresaId, 1);
        completarLaBandeja(procesoId, TiendaConMensajeria.EMPACAR);
        simulacionService.tick(empresaId, 1);
        simulacionService.tick(empresaId, 3);

        assertThat(casos(procesoId)).hasSize(PEDIDOS)
                .allSatisfy(caso -> assertThat(caso.estado()).isEqualTo(EstadoCaso.TERMINADO));
        assertThat(pasosDe(procesoId)).contains("Order shipped").doesNotContain(TiendaConMensajeria.CANCELAR);
        assertThat(simulacionService.panel(empresaId).salientesPendientes()).isZero();
    }

    @Test
    @DisplayName("Con la tasa de rechazo al cien, los veinte se cancelan y ninguno llega a bodega")
    void veintePedidosRechazados_terminanCancelados() {
        conRechazoDePagos(100);
        Long procesoId = demo.publicarLaDemo(empresaId, adminId, "Order fulfillment for twenty declined orders");

        simulacionService.pedidos(empresaId, procesoId, PEDIDOS, null);
        completarLaBandeja(procesoId, TiendaConMensajeria.REVISAR);
        simulacionService.tick(empresaId, 1);

        assertThat(casos(procesoId)).hasSize(PEDIDOS)
                .allSatisfy(caso -> assertThat(caso.estado()).isEqualTo(EstadoCaso.TERMINADO));
        assertThat(pasosDe(procesoId)).contains(TiendaConMensajeria.CANCELAR, "Order cancelled")
                .doesNotContain(TiendaConMensajeria.EMPACAR);
        assertThat(bandeja(procesoId)).isEmpty();
        conRechazoDePagos(0);
    }

    private void completarLaBandeja(Long procesoId, String nodo) {
        List<TareaResponse> tareas = bandeja(procesoId);
        assertThat(tareas).hasSize(PEDIDOS).allSatisfy(tarea ->
                assertThat(tarea.nodoNombre()).isEqualTo(nodo));
        tareas.forEach(tarea -> tareaService.completar(empresaId, adminId, tarea.id(), null));
    }

    private List<TareaResponse> bandeja(Long procesoId) {
        return tareaService.bandeja(empresaId, adminId, false, null, procesoId, null, Paginacion.de(0, 50))
                .content();
    }

    private List<CasoResponse> casos(Long procesoId) {
        return casoService.listar(empresaId, procesoId, null, null, Paginacion.de(0, 50)).content();
    }

    /** Por donde pasaron todos los pedidos de un proceso, sin repetir: es la forma del recorrido. */
    private List<String> pasosDe(Long procesoId) {
        return casos(procesoId).stream()
                .map(caso -> casoService.obtener(empresaId, caso.id()))
                .map(CasoDetalleResponse::pasos)
                .flatMap(List::stream)
                .map(PasoDelCasoResponse::nodoNombre)
                .distinct()
                .toList();
    }

    private void conRechazoDePagos(int tasa) {
        ConfiguracionTiendaResponse antes = configuracionTiendaService.obtener(empresaId);
        configuracionTiendaService.editar(empresaId, adminId, antes.politicaEstructura(), null,
                ParametrosSimulacion.builder().tasaRechazoPagos(tasa).build(), antes.version());
    }
}
