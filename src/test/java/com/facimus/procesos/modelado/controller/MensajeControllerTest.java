package com.facimus.procesos.modelado.controller;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.model.AccionSiFalla;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.TipoDeDato;
import com.facimus.procesos.modelado.model.TipoDestino;
import com.facimus.procesos.modelado.service.DatosDeMensaje;
import com.facimus.procesos.modelado.service.MensajeService;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(MensajeController.class)
class MensajeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MensajeService mensajeService;

    @Test
    @DisplayName("GET /api/v1/procesos/{procesoId}/mensajes - listar mensajes (200)")
    void listar_mensajes() throws Exception {
        MensajeResponse m = crearMensaje(1L, "Orden de compra");
        given(mensajeService.listarPorProceso(1L, 10L)).willReturn(List.of(m));

        mockMvc.perform(get("/api/v1/procesos/10/mensajes").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Orden de compra"));
    }

    @Test
    @DisplayName("GET /api/v1/mensajes/{id} - detalle mensaje (200)")
    void detalle_mensaje() throws Exception {
        MensajeResponse m = crearMensaje(1L, "Orden de compra");
        given(mensajeService.obtener(1L, 1L)).willReturn(m);

        mockMvc.perform(get("/api/v1/mensajes/1").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Orden de compra"));
    }

    @Test
    @DisplayName("GET /api/v1/mensajes/{id} - sin sesion retorna 401")
    void detalle_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/mensajes/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/procesos/{procesoId}/mensajes - crear mensaje (201)")
    void crear_mensaje() throws Exception {
        MensajeResponse m = crearMensaje(2L, "Factura");
        given(mensajeService.crear(eq(1L), eq(1L), eq(10L), any(DatosDeMensaje.class))).willReturn(m);

        mockMvc.perform(post("/api/v1/procesos/10/mensajes")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Factura","contenido":"Datos de factura","poolOrigenId":1,"poolDestinoId":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/mensajes/2"))
                .andExpect(jsonPath("$.nombre").value("Factura"));
    }

    @Test
    @DisplayName("POST /api/v1/procesos/{procesoId}/mensajes - validacion falla (400)")
    void crear_validacion_falla() throws Exception {
        mockMvc.perform(post("/api/v1/procesos/10/mensajes")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"","contenido":"","poolOrigenId":null,"poolDestinoId":null}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/mensajes/{id} - editar mensaje (200)")
    void editar_mensaje() throws Exception {
        MensajeResponse m = crearMensaje(1L, "Orden actualizada");
        given(mensajeService.editar(eq(1L), eq(1L), eq(1L), any(DatosDeMensaje.class), eq(3L)))
                .willReturn(m);

        mockMvc.perform(put("/api/v1/mensajes/1")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Orden actualizada","contenido":"Nuevo contenido","version":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Orden actualizada"));
    }

    @Test
    @DisplayName("DELETE /api/v1/mensajes/{id} - eliminar mensaje (204)")
    void eliminar_mensaje() throws Exception {
        doNothing().when(mensajeService).eliminar(1L, 1L, 1L);

        mockMvc.perform(delete("/api/v1/mensajes/1").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/procesos/{procesoId}/mensajes - crear mensaje anclado y con campos (201)")
    void crear_mensaje_anclado() throws Exception {
        given(mensajeService.crear(eq(1L), eq(1L), eq(10L), any(DatosDeMensaje.class)))
                .willReturn(crearMensaje(2L, "Payment authorization request"));

        mockMvc.perform(post("/api/v1/procesos/10/mensajes")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Payment authorization request","contenido":"Total y tarjeta",
                                 "poolOrigenId":1,"poolDestinoId":2,"nodoOrigenId":12,
                                 "tipoDestino":"SERVICIO_WEB","siFalla":"MANEJAR_ERROR","nodoManejoErrorId":18,
                                 "campos":[{"nombre":"orderId","tipo":"TEXTO"}],"variable":"paymentRequest"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoDestino").value("SERVICIO_WEB"));
    }

    @Test
    @DisplayName("POST /api/v1/procesos/{procesoId}/mensajes - un campo sin tipo no pasa la validacion (400)")
    void crear_mensaje_campoSinTipo() throws Exception {
        mockMvc.perform(post("/api/v1/procesos/10/mensajes")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Order placed","contenido":"Carrito","poolOrigenId":1,"poolDestinoId":2,
                                 "campos":[{"nombre":"orderId"}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    private MensajeResponse crearMensaje(Long id, String nombre) {
        return new MensajeResponse(id, nombre, "Contenido test", 1L, 2L, 12L, null,
                TipoDestino.SERVICIO_WEB, AccionSiFalla.MANEJAR_ERROR, 18L, false,
                List.of(new CampoDeMensaje("orderId", TipoDeDato.TEXTO)), null, "paymentRequest", null,
                10L, 0L, null, null, null, null);
    }

}
