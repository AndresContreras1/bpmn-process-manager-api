package com.facimus.procesos.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.security.SesionEnCookies;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 35: anonimizar a una persona a pedido. Su fila se queda, porque la nombran la autoria y el historial, pero sin
 * nada que la identifique: ni nombre, ni correo, ni una clave que sirva, ni sesiones, ni roles. El historial que la
 * nombraba la nombra ahora con su seudonimo, y su correo queda libre.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AnonimizacionIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";
    private static final String CLAVE_DE_LUCIA = "lluvia-sobre-los-tejados";
    private static int tiendas;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private JdbcTemplate jdbc;

    private Long empresaId;
    private String acceso;
    private String lucia;

    @BeforeEach
    void registrarTienda() throws Exception {
        int numero = ++tiendas;
        String admin = "admin" + numero + "@anonimizar.test";
        lucia = "lucia.fernandez" + numero + "@anonimizar.test";
        empresaId = empresaService.registrar("Tienda que anonimiza " + numero, "9010" + numero + "000-1", admin,
                "Administradora", admin, CLAVE).id();
        acceso = entrar(admin, CLAVE);
    }

    @Test
    @DisplayName("Anonimizar quita el nombre, el correo, la clave, las sesiones y los roles, y el historial la nombra "
            + "con su seudonimo")
    void anonimizar_borraLosDatosPersonales() throws Exception {
        // Una invitacion a su correo deja ese correo en el historial de la tienda
        jdbc.update("update usuarios set correo_verificado_en = now() where empresa_id = ?", empresaId);
        mockMvc.perform(post("/api/v1/usuarios/invitaciones").with(SesionEnCookies.conSesion(acceso))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("email", lucia, "rolAcceso", "EDITOR"))))
                .andExpect(status().isAccepted());
        Long id = crearALucia();
        enlace(id, lucia, "RECUPERAR_CLAVE");
        enlace(null, lucia, "INVITACION");
        String suSesion = entrar(lucia, CLAVE_DE_LUCIA);
        Long rol = crearRol("Packing");
        mockMvc.perform(put("/api/v1/usuarios/{id}/roles-proceso", id).with(SesionEnCookies.conSesion(acceso))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("rolesProcesoIds", List.of(rol)))))
                .andExpect(status().isOk());

        anonimizar(id).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/usuarios/{id}", id).with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Persona anonimizada " + id))
                .andExpect(jsonPath("$.email").value("anonimizada-" + id + "@anonimo.invalid"))
                .andExpect(jsonPath("$.activo").value(false));
        mockMvc.perform(get("/api/v1/usuarios/{id}/roles-proceso", id).with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(suSesion)))
                .andExpect(status().isUnauthorized());
        login(lucia, CLAVE_DE_LUCIA).andExpect(status().isUnauthorized());

        List<String> historial = historial();
        assertThat(historial).noneMatch(linea -> linea.contains("Lucía") || linea.contains(lucia))
                .anyMatch(linea -> linea.contains("\"Persona anonimizada " + id + "\""))
                .anyMatch(linea -> linea.contains("anonimizada-" + id + "@anonimo.invalid"))
                .contains("Usuario anonimizado: su nombre y su correo ya no constan en ninguna parte.");
        assertThat(jdbc.queryForObject("select count(*) from usuarios where nombre like '%Lucía%' or email = ?",
                Integer.class, lucia)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from enlaces_de_un_uso where usuario_id = ? or email = ?",
                Integer.class, id, lucia)).isZero();
    }

    @Test
    @DisplayName("Su correo queda libre: alguien se puede registrar con el")
    void anonimizar_liberaElCorreo() throws Exception {
        anonimizar(crearALucia()).andExpect(status().isNoContent());

        crearUsuario("Lucía otra vez", lucia).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Nadie se anonimiza a si mismo")
    void anonimizarse_esUnConflicto() throws Exception {
        Long propio = jsonMapper.readTree(mockMvc.perform(get("/api/v1/usuarios").with(SesionEnCookies.conSesion(acceso)))
                .andReturn().getResponse().getContentAsString()).get("content").get(0).get("id").asLong();

        anonimizar(propio).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A una persona anonimizada no se la edita, ni se la reactiva, ni se le da otra clave, y anonimizarla otra "
            + "vez no cambia nada")
    void anonimizada_noVuelve() throws Exception {
        Long id = crearALucia();
        anonimizar(id).andExpect(status().isNoContent());
        long version = jsonMapper.readTree(mockMvc.perform(get("/api/v1/usuarios/{id}", id)
                .with(SesionEnCookies.conSesion(acceso))).andReturn().getResponse().getContentAsString())
                .get("version").asLong();

        mockMvc.perform(patch("/api/v1/usuarios/{id}", id).with(SesionEnCookies.conSesion(acceso))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("activo", true, "version", version))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/usuarios/{id}/restablecer-clave", id).with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isConflict());
        anonimizar(id).andExpect(status().isNoContent());
        assertThat(historial()).filteredOn(linea -> linea.startsWith("Usuario anonimizado")).hasSize(1);
    }

    private Long crearALucia() throws Exception {
        String respuesta = crearUsuario("Lucía Fernández", lucia).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(respuesta).get("id").asLong();
    }

    private ResultActions crearUsuario(String nombre, String correo) throws Exception {
        return mockMvc.perform(post("/api/v1/usuarios").with(SesionEnCookies.conSesion(acceso))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("nombre", nombre, "email", correo,
                        "password", CLAVE_DE_LUCIA, "rolAcceso", "EDITOR"))));
    }

    private Long crearRol(String nombre) throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/roles").with(SesionEnCookies.conSesion(acceso))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("nombre", nombre, "descripcion",
                                "Packs the orders"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(respuesta).get("id").asLong();
    }

    /** Un enlace del correo sin usar, escrito en la base como lo deja el trabajo que manda el correo. */
    private void enlace(Long usuarioId, String correo, String proposito) {
        jdbc.update("insert into enlaces_de_un_uso (empresa_id, usuario_id, email, proposito, rol_acceso, token_hash, "
                + "creado_en, vence_en) values (?, ?, ?, ?, 'EDITOR', ?, now(), now() + interval '1 day')", empresaId,
                usuarioId, correo, proposito, Huella.de(correo + proposito));
    }

    private ResultActions anonimizar(Long id) throws Exception {
        return mockMvc.perform(post("/api/v1/usuarios/{id}/anonimizar", id).with(SesionEnCookies.conSesion(acceso)));
    }

    /** Lo que dice el historial de la tienda, de lo mas nuevo a lo mas viejo. */
    private List<String> historial() throws Exception {
        JsonNode pagina = jsonMapper.readTree(mockMvc.perform(get("/api/v1/empresas/actual/historial")
                        .with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return pagina.get("content").valueStream().map(linea -> linea.get("descripcionCambio").asString()).toList();
    }

    private ResultActions login(String correo, String clave) throws Exception {
        return mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new LoginRequest(correo, clave))));
    }

    private String entrar(String correo, String clave) throws Exception {
        return SesionEnCookies.acceso(login(correo, clave).andExpect(status().isOk()));
    }
}
