package com.facimus.procesos.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.ejecucion.TiendaConMensajeria;
import com.facimus.procesos.ejecucion.service.DatosDelEntrante;
import com.facimus.procesos.ejecucion.service.MensajeriaService;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.model.ModoSimulacion;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoCompartidoService;
import com.facimus.procesos.security.SesionEnCookies;

import tools.jackson.databind.json.JsonMapper;

/**
 * PR 35: la baja de una tienda. Pedirla deja 30 dias de solo lectura, en los que se puede cancelar; cumplidos, la purga
 * borra cada fila de la tienda, de su fila queda una lapida, y las demas tiendas no pierden nada mas que lo que le
 * compartian. El paso de los dias se hace en la base, moviendo cuando se pidio la baja.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BajaDeTiendaIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";
    private static int tiendas;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ConfiguracionTiendaService configuracionTiendaService;

    @Autowired
    private ProcesoCompartidoService procesoCompartidoService;

    @Autowired
    private MensajeriaService mensajeriaService;

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Dar de baja pide el nombre de la tienda, dice cuando se borrara, y no se pide dos veces")
    void pedirLaBaja_conElNombreDeLaTienda() throws Exception {
        Tienda tienda = registrar();
        String acceso = entrar(tienda.admin());

        baja(acceso, "Otra tienda").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("nombre de la tienda")));
        baja(acceso, tienda.nombre())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bajaSolicitadaEn").isNotEmpty())
                .andExpect(jsonPath("$.borradoProgramadoPara").value(startsWith(LocalDate.now().plusDays(30).toString())));
        baja(acceso, tienda.nombre()).andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/empresas/actual").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.borradoProgramadoPara").isNotEmpty());
        assertThat(jdbc.queryForObject("select count(*) from historial_cambios where empresa_id = ? and "
                + "descripcion_cambio like 'Tienda dada de baja:%'", Integer.class, tienda.id())).isOne();
    }

    @Test
    @DisplayName("Durante la gracia solo se consulta: los cambios responden 409, la sesion sigue, y cancelar la baja "
            + "devuelve la tienda")
    void duranteLaGracia_soloLectura() throws Exception {
        Tienda tienda = registrar();
        ResultActions login = login(tienda.admin());
        String acceso = SesionEnCookies.acceso(login);
        crearProceso(acceso, "Before closing").andExpect(status().isCreated());
        baja(acceso, tienda.nombre()).andExpect(status().isOk());

        crearProceso(acceso, "While closing")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Tienda dada de baja"))
                .andExpect(jsonPath("$.detail").value(containsString("cancelar la baja")));
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(acceso))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/refresh").with(SesionEnCookies.conRefresco(SesionEnCookies.refresco(login))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/logout").with(SesionEnCookies.conSesion(entrar(tienda.admin()))))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/empresas/actual/baja").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bajaSolicitadaEn").doesNotExist());
        crearProceso(acceso, "After cancelling").andExpect(status().isCreated());
        mockMvc.perform(delete("/api/v1/empresas/actual/baja").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("El reloj automatico deja de mover a una tienda dada de baja, y la vuelve a mover si la baja se cancela")
    void elRelojAutomatico_noMueveUnaTiendaDeBaja() {
        Tienda tienda = registrar();
        var antes = configuracionTiendaService.obtener(tienda.id());
        configuracionTiendaService.editar(tienda.id(), tienda.adminId(), antes.politicaEstructura(),
                ModoSimulacion.AUTOMATICO, null, antes.version());

        empresaService.pedirBaja(tienda.id(), tienda.adminId(), tienda.nombre());
        assertThat(configuracionTiendaService.tiendasEnAutomatico()).doesNotContain(tienda.id());

        empresaService.cancelarBaja(tienda.id(), tienda.adminId());
        assertThat(configuracionTiendaService.tiendasEnAutomatico()).contains(tienda.id());
    }

    @Test
    @DisplayName("Una tienda dada de baja no se da de baja otra vez: la API la frena por ser de solo lectura, y el "
            + "service tambien")
    void dosBajas_sonUnConflicto() {
        Tienda tienda = registrar();
        empresaService.pedirBaja(tienda.id(), tienda.adminId(), tienda.nombre());

        assertThatThrownBy(() -> empresaService.pedirBaja(tienda.id(), tienda.adminId(), tienda.nombre()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ya está dada de baja");
    }

    @Test
    @DisplayName("Dentro de los 30 dias la purga no borra nada")
    void dentroDeLaGracia_noSeBorra() {
        Tienda tienda = registrar();
        empresaService.pedirBaja(tienda.id(), tienda.adminId(), tienda.nombre());
        pedidaHace(tienda, 29);

        assertThat(empresaService.borrarLasDadasDeBaja()).isZero();
        assertThat(usuarioRepository.findByEmail(tienda.admin())).isPresent();
    }

    @Test
    @DisplayName("Cumplida la gracia, la purga borra cada fila de la tienda, deja una lapida, y otra tienda solo pierde "
            + "lo que le compartia")
    void cumplidaLaGracia_seBorraTodo() throws Exception {
        Tienda seVa = registrar();
        Tienda seQueda = registrar();
        TiendaConMensajeria demo = new TiendaConMensajeria(contexto);
        Long procesoQueSeVa = demo.publicarLaDemo(seVa.id(), seVa.adminId(), "Fulfillment that goes");
        Long procesoQueSeQueda = demo.publicarLaDemo(seQueda.id(), seQueda.adminId(), "Fulfillment that stays");
        mensajeriaService.recibir(seVa.id(), procesoQueSeVa, DatosDelEntrante.aMano(TiendaConMensajeria.PEDIDO, null,
                Map.of("orderId", "ORD-BAJA-1"), "baja-1"));
        mensajeriaService.recibir(seQueda.id(), procesoQueSeQueda, DatosDelEntrante.aMano(TiendaConMensajeria.PEDIDO,
                null, Map.of("orderId", "ORD-BAJA-2"), "baja-2"));
        procesoCompartidoService.compartir(seVa.id(), procesoQueSeVa, seVa.adminId(), seQueda.nit());
        procesoCompartidoService.compartir(seQueda.id(), procesoQueSeQueda, seQueda.adminId(), seVa.nit());
        entrar(seVa.admin());
        Map<String, Integer> deLaQueSeQueda = filasPorTabla(seQueda.id());
        assertThat(filasPorTabla(seVa.id()))
                .as("la tienda que se va tiene de todo antes de irse")
                .extractingByKeys("procesos", "versiones_proceso", "casos", "actividades_caso", "eventos_caso",
                        "mensajes_entrantes", "procesos_compartidos", "historial_cambios", "sesiones",
                        "refresh_tokens", "trabajos", "roles_proceso", "configuracion_tienda", "usuarios")
                .allSatisfy(filas -> assertThat(filas).isPositive());

        empresaService.pedirBaja(seVa.id(), seVa.adminId(), seVa.nombre());
        pedidaHace(seVa, 31);
        assertThat(empresaService.borrarLasDadasDeBaja()).isOne();

        assertThat(filasPorTabla(seVa.id())).allSatisfy((tabla, filas) -> assertThat(filas).as(tabla).isZero());
        assertThat(jdbc.queryForObject("select count(*) from procesos_compartidos where empresa_invitada_id = ?",
                Integer.class, seVa.id())).isZero();
        Map<String, Integer> esperado = new HashMap<>(deLaQueSeQueda);
        esperado.merge("procesos_compartidos", -1, Integer::sum);
        assertThat(filasPorTabla(seQueda.id())).isEqualTo(esperado);

        Map<String, Object> lapida = jdbc.queryForMap(
                "select nombre, nit, correo_contacto, borrada_en from empresas where id = ?", seVa.id());
        assertThat(lapida).containsEntry("nombre", "Tienda borrada")
                .containsEntry("nit", "borrada-" + seVa.id())
                .containsEntry("correo_contacto", "borrada-" + seVa.id() + "@borrada.invalid");
        assertThat(lapida.get("borrada_en")).isNotNull();
        mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(seVa.admin(), CLAVE))))
                .andExpect(status().isUnauthorized());
        assertThat(empresaService.borrarLasDadasDeBaja()).as("una lapida no se vuelve a borrar").isZero();
        // Su NIT y su correo quedaron libres
        assertThat(empresaService.registrar(seVa.nombre(), seVa.nit(), seVa.admin(), "Administradora", seVa.admin(),
                CLAVE).id()).isNotEqualTo(seVa.id());
    }

    private Tienda registrar() {
        int numero = ++tiendas;
        String nombre = "Tienda que se va " + numero;
        String admin = "admin" + numero + "@baja.test";
        String nit = "9009" + numero + "000-1";
        Long id = empresaService.registrar(nombre, nit, admin, "Administradora", admin, CLAVE).id();
        return new Tienda(id, usuarioRepository.findByEmail(admin).orElseThrow().getId(), nombre, admin, nit);
    }

    private void pedidaHace(Tienda tienda, int dias) {
        // En UTC, como Hibernate guarda las fechas (hibernate.jdbc.time_zone)
        jdbc.update("update empresas set baja_solicitada_en = ? where id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusDays(dias),
                tienda.id());
    }

    /** Cuantas filas tiene la tienda en cada tabla con empresa_id, segun el catalogo de la base. */
    private Map<String, Integer> filasPorTabla(Long empresaId) {
        List<String> tablas = jdbc.queryForList("select table_name from information_schema.columns "
                + "where table_schema = current_schema() and column_name = 'empresa_id'", String.class);
        Map<String, Integer> filas = new HashMap<>();
        tablas.forEach(tabla -> filas.put(tabla, jdbc.queryForObject(
                "select count(*) from " + tabla + " where empresa_id = ?", Integer.class, empresaId)));
        return filas;
    }

    private ResultActions login(String correo) throws Exception {
        return mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(correo, CLAVE))))
                .andExpect(status().isOk());
    }

    private String entrar(String correo) throws Exception {
        return SesionEnCookies.acceso(login(correo));
    }

    private ResultActions baja(String acceso, String confirmacion) throws Exception {
        return mockMvc.perform(post("/api/v1/empresas/actual/baja").with(SesionEnCookies.conSesion(acceso))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("confirmacion", confirmacion))));
    }

    private ResultActions crearProceso(String acceso, String nombre) throws Exception {
        return mockMvc.perform(post("/api/v1/procesos").with(SesionEnCookies.conSesion(acceso))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("nombre", nombre, "descripcion", "Closing test",
                        "categoria", "Fulfillment"))));
    }

    private record Tienda(Long id, Long adminId, String nombre, String admin, String nit) {
    }
}
