package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.ComparacionResponse;
import com.facimus.procesos.modelado.dto.response.ComparacionResponse.Campo;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.ComparacionService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.DiagramaService;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.GuardadoDelModeloService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.ModeloBpmnService;
import com.facimus.procesos.modelado.service.PoolService;

/** PR 57: comparar versiones, el borrador y un archivo sin guardarlo. */
@SpringBootTest
@ActiveProfiles("test")
class ComparacionIntegracionTest {

    private static int tiendas;

    @Autowired
    private EmpresaService empresaService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ProcesoService procesoService;
    @Autowired
    private RolProcesoService rolProcesoService;
    @Autowired
    private PoolService poolService;
    @Autowired
    private LaneService laneService;
    @Autowired
    private ActividadService actividadService;
    @Autowired
    private EventoService eventoService;
    @Autowired
    private ArcoService arcoService;
    @Autowired
    private ComparacionService comparacionService;
    @Autowired
    private ModeloBpmnService modeloBpmnService;
    @Autowired
    private GuardadoDelModeloService guardado;
    @Autowired
    private DiagramaService diagramaService;

    private Long empresaId;
    private Long adminId;
    private Long procesoId;
    private Long ventas;
    private ActividadResponse tarea;

    @BeforeEach
    void modelar() {
        int numero = ++tiendas;
        String admin = "admin" + numero + "@comparar.test";
        empresaId = empresaService.registrar("Tienda que compara " + numero, "9480" + numero + "000-1", admin,
                "Carla Compara", admin, "marea-violeta-del-sur").id();
        adminId = usuarioRepository.findByEmail(admin).orElseThrow().getId();
        procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Checkout to delivery",
                "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        ventas = laneService.crear(empresaId, adminId, tienda, "Sales",
                rolProcesoService.crear(empresaId, adminId, "Sales", null).id()).id();
        Long inicio = eventoService.crear(empresaId, adminId, ventas, "Start", TipoEvento.INICIO, 0, 0).id();
        tarea = actividadService.crear(empresaId, adminId, ventas, "Review", null, TipoActividad.USUARIO, 180, 0);
        Long fin = eventoService.crear(empresaId, adminId, ventas, "End", TipoEvento.FIN, 360, 0).id();
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, tarea.id()));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(tarea.id(), fin));
    }

    private void publicar() {
        procesoService.cambiarEstado(empresaId, procesoId, adminId, EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, procesoId, false).version());
    }

    private void renombrar(String nombre) {
        ActividadResponse actual = actividadService.obtener(empresaId, tarea.id());
        actividadService.editar(empresaId, adminId, tarea.id(), nombre, null, TipoActividad.USUARIO, null,
                actual.posicionX(), actual.posicionY(), actual.version());
    }

    @Test
    @DisplayName("Lo publicado contra el borrador dice que se renombro")
    void vigenteContraBorrador() {
        publicar();
        renombrar("Check");

        ComparacionResponse comparacion = comparacionService.comparar(empresaId, procesoId, "vigente", "borrador");

        assertThat(comparacion.cambiados()).singleElement().satisfies(cambiado -> {
            assertThat(cambiado.bpmnId()).isEqualTo(tarea.bpmnId());
            assertThat(cambiado.campos()).containsExactly(new Campo("nombre", "Review", "Check"));
        });
    }

    @Test
    @DisplayName("Dos versiones se comparan entre si, y una consigo misma no tiene diferencias")
    void entreVersiones() {
        publicar();
        renombrar("Check");
        publicar();

        assertThat(comparacionService.comparar(empresaId, procesoId, "1", "2").cambiados()).hasSize(1);
        assertThat(comparacionService.comparar(empresaId, procesoId, "2", "2").sinDiferencias()).isTrue();
        assertThat(comparacionService.comparar(empresaId, procesoId, "2", "borrador").sinDiferencias()).isTrue();
    }

    @Test
    @DisplayName("Sin version vigente, o pidiendo algo que no es una version, se dice")
    void errores() {
        assertThatThrownBy(() -> comparacionService.comparar(empresaId, procesoId, "vigente", "borrador"))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> comparacionService.comparar(empresaId, procesoId, "ayer", "borrador"))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> comparacionService.comparar(empresaId, 999_999L, "1", "borrador"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Comparar un archivo dice lo que haria, y no guarda nada")
    void archivoSinGuardar() {
        String xml = modeloBpmnService.exportar(empresaId, procesoId).xml();
        String cambiado = xml.replace("Review", "Check");

        ComparacionResponse comparacion = comparacionService.compararArchivo(empresaId, adminId, procesoId, entrada(cambiado));

        assertThat(comparacion.cambiados()).singleElement().satisfies(uno -> {
            assertThat(uno.tipo()).isEqualTo("ACTIVIDAD");
            assertThat(uno.campos()).containsExactly(new Campo("nombre", "Review", "Check"));
        });
        assertThat(modeloBpmnService.exportar(empresaId, procesoId).xml()).isEqualTo(xml);
        assertThat(actividadService.obtener(empresaId, tarea.id()).nombre()).isEqualTo("Review");
    }

    @Test
    @DisplayName("Lo que se restauro no aparece como cambiado aunque tenga otras filas")
    void restaurado() {
        var punto = guardado.puntoDeGuardado(empresaId, adminId, procesoId,
                com.facimus.procesos.modelado.model.MotivoDeRevision.CIERRE);
        publicar();
        actividadService.eliminar(empresaId, adminId, tarea.id());
        guardado.restaurar(empresaId, adminId, RolAcceso.ADMINISTRADOR, procesoId, punto.id());

        assertThat(diagramaService.obtener(empresaId, procesoId).actividades().getFirst().id()).isNotEqualTo(tarea.id());
        assertThat(comparacionService.comparar(empresaId, procesoId, "vigente", "borrador").sinDiferencias()).isTrue();
    }

    private static ByteArrayInputStream entrada(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
