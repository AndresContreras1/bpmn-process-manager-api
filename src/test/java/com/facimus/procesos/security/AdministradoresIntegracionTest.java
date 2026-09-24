package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.UsuarioService;

import tools.jackson.databind.json.JsonMapper;

/** Toda tienda conserva un administrador activo, aunque dos administradores cambien sus permisos a la vez. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AdministradoresIntegracionTest {

    private static final String CLAVE = "clave12345";
    private static final String ULTIMO_ADMINISTRADOR =
            "La tienda tiene que conservar al menos un administrador activo.";
    private static final String PROPIA_CUENTA = "No puede desactivar su propia cuenta.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("El unico administrador no puede quitarse el rol; con otro administrador activo, si")
    void unicoAdministrador_noPuedeQuitarseElRol() throws Exception {
        Long empresaId = registrarTienda("unico", "900161616-1");
        Long adminId = idDe("admin@unico.com");
        String token = iniciarSesion("admin@unico.com");

        // Confirmar su rol o su estado no le quita el administrador a la tienda.
        pedir(patch("/api/v1/usuarios/{id}", adminId), token, Map.of("rolAcceso", "ADMINISTRADOR", "version", 0))
                .andExpect(status().isOk());
        pedir(patch("/api/v1/usuarios/{id}", adminId), token, Map.of("activo", true, "version", 0))
                .andExpect(status().isOk());
        pedir(patch("/api/v1/usuarios/{id}", adminId), token, Map.of("rolAcceso", "EDITOR", "version", 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ULTIMO_ADMINISTRADOR));

        usuarioService.crearColaborador(empresaId, null, "Segunda", "segunda@unico.com", CLAVE, RolAcceso.ADMINISTRADOR);
        pedir(patch("/api/v1/usuarios/{id}", adminId), token, Map.of("rolAcceso", "EDITOR", "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolAcceso").value("EDITOR"));
    }

    @Test
    @DisplayName("Nadie desactiva su propia cuenta, ni con DELETE ni con PATCH; a otro administrador, si")
    void nadieDesactivaSuPropiaCuenta() throws Exception {
        Long empresaId = registrarTienda("propia", "900161617-2");
        Long adminId = idDe("admin@propia.com");
        Long otroId = usuarioService.crearColaborador(empresaId, null, "Otro", "otro@propia.com", CLAVE,
                RolAcceso.ADMINISTRADOR).id();
        String token = iniciarSesion("admin@propia.com");

        pedir(delete("/api/v1/usuarios/{id}", adminId), token, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(PROPIA_CUENTA));
        pedir(patch("/api/v1/usuarios/{id}", adminId), token, Map.of("activo", false, "version", 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(PROPIA_CUENTA));
        pedir(delete("/api/v1/usuarios/{id}", otroId), token, null)
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Si dos administradores se quitan el rol a la vez, el segundo espera y la tienda conserva uno")
    void dosAdministradoresALaVez_laTiendaConservaUno() throws Exception {
        Long empresaId = registrarTienda("concurrente", "900161618-3");
        Long ana = idDe("admin@concurrente.com");
        Long beto = usuarioService.crearColaborador(empresaId, null, "Beto", "beto@concurrente.com", CLAVE,
                RolAcceso.ADMINISTRADOR).id();
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);
        CountDownLatch betoCambiado = new CountDownLatch(1);
        CountDownLatch terminar = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            // Ana le quita el rol a Beto y su transaccion sigue abierta.
            Future<?> primero = hilos.submit(() -> transaccion.executeWithoutResult(estado -> {
                usuarioService.actualizar(empresaId, ana, beto, null, RolAcceso.EDITOR, null, 0L);
                betoCambiado.countDown();
                esperar(terminar);
            }));
            assertThat(betoCambiado.await(10, TimeUnit.SECONDS)).isTrue();

            // Beto le quita el rol a Ana al mismo tiempo: espera a que el primer cambio termine.
            Future<UsuarioResponse> segundo = hilos.submit(
                    () -> usuarioService.actualizar(empresaId, beto, ana, null, RolAcceso.EDITOR, null, 0L));
            assertThatThrownBy(() -> segundo.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            terminar.countDown();
            primero.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> segundo.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ReglaNegocioException.class)
                    .hasRootCauseMessage(ULTIMO_ADMINISTRADOR);
        } finally {
            terminar.countDown();
            hilos.shutdownNow();
        }
        assertThat(usuarioRepository.countByEmpresaIdAndRolAccesoAndActivoTrue(empresaId, RolAcceso.ADMINISTRADOR))
                .isEqualTo(1);
    }

    private Long registrarTienda(String nombre, String nit) {
        return empresaService.registrar("Tienda " + nombre, nit, "contacto@" + nombre + ".com", "Administradora",
                "admin@" + nombre + ".com", CLAVE).id();
    }

    private Long idDe(String email) {
        return usuarioRepository.findByEmail(email).orElseThrow().getId();
    }

    private String iniciarSesion(String email) throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(respuesta).get("accessToken").asString();
    }

    private ResultActions pedir(MockHttpServletRequestBuilder peticion, String token, Map<String, Object> cuerpo)
            throws Exception {
        peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(cuerpo));
        }
        return mockMvc.perform(peticion);
    }

    private static void esperar(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
