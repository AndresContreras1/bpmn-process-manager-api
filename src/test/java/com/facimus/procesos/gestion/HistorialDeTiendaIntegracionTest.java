package com.facimus.procesos.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;

/**
 * D15: el historial deja de ser solo de los procesos. Lo que pasa en la tienda (altas, roles, el registro) se anota
 * igual, con su autor y diciendo de que recurso habla.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistorialDeTiendaIntegracionTest {

    private static final String CLAVE = "clave12345";

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolProcesoService rolProcesoService;

    @Autowired
    private ProcesoService procesoService;

    @Autowired
    private HistorialCambioService historialCambioService;

    private Long empresaId;
    private Long adminId;

    @BeforeAll
    void registrarTienda() {
        empresaId = empresaService.registrar("Tienda con historial", "900353637-8", "contacto@historial.com",
                "Administradora", "admin@historial.com", CLAVE).id();
        adminId = usuarioRepository.findByEmail("admin@historial.com").orElseThrow().getId();
    }

    @Test
    @DisplayName("El registro de la tienda deja su propia linea y la del primer administrador, que se crea a si mismo")
    void registrar_dejaLaTiendaYSuAdministrador() {
        assertThat(historial())
                .extracting(HistorialCambioResponse::recursoTipo, HistorialCambioResponse::descripcionCambio,
                        HistorialCambioResponse::autorNombre)
                .contains(tuple(RecursoDeHistorial.EMPRESA, "Tienda \"Tienda con historial\" registrada.",
                                "Administradora"),
                        tuple(RecursoDeHistorial.USUARIO, "Usuario \"Administradora\" creado con rol ADMINISTRADOR.",
                                "Administradora"));
    }

    @Test
    @DisplayName("HU-02.4: el alta de un colaborador, su cambio de rol y su baja quedan en el historial")
    void usuarios_dejanSuRastro() {
        Long editoraId = usuarioService.crearColaborador(empresaId, adminId, "Editora", "editora@historial.com",
                CLAVE, RolAcceso.EDITOR).id();
        Long version = usuarioService.obtener(empresaId, editoraId).version();
        usuarioService.actualizar(empresaId, adminId, editoraId, RolAcceso.SOLO_LECTURA, null, version);
        usuarioService.desactivar(empresaId, adminId, editoraId);

        assertThat(historial())
                .filteredOn(linea -> linea.recursoTipo() == RecursoDeHistorial.USUARIO
                        && editoraId.equals(linea.recursoId()))
                .extracting(HistorialCambioResponse::descripcionCambio)
                .containsExactly("Usuario \"Editora\" desactivado.",
                        "Usuario \"Editora\" con rol SOLO_LECTURA.",
                        "Usuario \"Editora\" creado con rol EDITOR.");
    }

    @Test
    @DisplayName("HU-18.5 y HU-19.4: crear, renombrar y eliminar un rol de proceso queda en el historial")
    void roles_dejanSuRastro() {
        Long rolId = rolProcesoService.crear(empresaId, adminId, "Ventas", "Atiende pedidos").id();
        Long version = rolProcesoService.obtener(empresaId, rolId).version();
        rolProcesoService.editar(empresaId, adminId, rolId, "Ventas online", "Atiende pedidos", version);
        rolProcesoService.eliminar(empresaId, adminId, rolId);

        assertThat(historial())
                .filteredOn(linea -> linea.recursoTipo() == RecursoDeHistorial.ROL && rolId.equals(linea.recursoId()))
                .extracting(HistorialCambioResponse::descripcionCambio)
                .containsExactly("Rol de proceso \"Ventas online\" eliminado.",
                        "Rol de proceso \"Ventas\" renombrado a \"Ventas online\".",
                        "Rol de proceso \"Ventas\" creado.");
    }

    @Test
    @DisplayName("Lo que pasa en un proceso tambien esta en el historial de la tienda, sin salirse del proceso")
    void procesos_estanEnLosDosHistoriales() {
        Long procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Checkout to delivery",
                "Fulfillment").id();

        assertThat(historial())
                .filteredOn(linea -> procesoId.equals(linea.recursoId())
                        && linea.recursoTipo() == RecursoDeHistorial.PROCESO)
                .extracting(HistorialCambioResponse::descripcionCambio)
                .containsExactly("Proceso creado.");
        assertThat(procesoService.listarHistorial(empresaId, procesoId))
                .extracting(HistorialCambioResponse::descripcionCambio)
                .containsExactly("Proceso creado.");
    }

    @Test
    @DisplayName("El historial de una tienda no cuenta nada de las demas")
    void historial_esDeCadaTienda() {
        Long otraId = empresaService.registrar("Otra tienda", "900353637-9", "contacto@otra.com", "Otro",
                "admin@otra.com", CLAVE).id();

        assertThat(historialCambioService.listarDeLaTienda(otraId, Paginacion.de(0, 50)).content())
                .extracting(HistorialCambioResponse::descripcionCambio)
                .containsExactlyInAnyOrder("Tienda \"Otra tienda\" registrada.",
                        "Usuario \"Otro\" creado con rol ADMINISTRADOR.");
    }

    @Test
    @DisplayName("El historial llega paginado, de lo mas reciente a lo mas viejo")
    void historial_llegaPaginado() {
        PageResponse<HistorialCambioResponse> primera = historialCambioService.listarDeLaTienda(empresaId,
                Paginacion.de(0, 2));

        assertThat(primera.content()).hasSize(2);
        assertThat(primera.totalElements()).isGreaterThan(2);
        assertThat(primera.content().getFirst().fechaCambio())
                .isAfterOrEqualTo(primera.content().getLast().fechaCambio());
    }

    private List<HistorialCambioResponse> historial() {
        return historialCambioService.listarDeLaTienda(empresaId, Paginacion.de(0, 100)).content();
    }
}
