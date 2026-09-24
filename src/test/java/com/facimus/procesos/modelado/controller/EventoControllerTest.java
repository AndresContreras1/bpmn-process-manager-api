package com.facimus.procesos.modelado.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.EventoService;

@WebMvcTest(EventoController.class)
class EventoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventoService eventoService;

    @Test
    @DisplayName("POST /api/v1/lanes/{laneId}/eventos - crear evento (201)")
    void crear_evento() throws Exception {
        given(eventoService.crear(eq(1L), eq(1L), eq(3L), anyString(), eq(TipoEvento.MENSAJE_INICIO), anyInt(),
                anyInt())).willReturn(crearEvento(1L, "Order received", TipoEvento.MENSAJE_INICIO));

        mockMvc.perform(post("/api/v1/lanes/3/eventos")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Order received","tipoEvento":"MENSAJE_INICIO","posicionX":20,
                                 "posicionY":80}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/eventos/1"))
                .andExpect(jsonPath("$.nombre").value("Order received"))
                .andExpect(jsonPath("$.tipoEvento").value("MENSAJE_INICIO"));
    }

    @Test
    @DisplayName("POST /api/v1/lanes/{laneId}/eventos - validacion falla (400)")
    void crear_validacion_falla() throws Exception {
        mockMvc.perform(post("/api/v1/lanes/3/eventos")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"","tipoEvento":null,"posicionX":0,"posicionY":0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/lanes/{laneId}/eventos - tipo de evento desconocido (400)")
    void crear_tipoDeEventoDesconocido() throws Exception {
        mockMvc.perform(post("/api/v1/lanes/3/eventos")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Order received","tipoEvento":"TEMPORIZADOR","posicionX":0,"posicionY":0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/lanes/{laneId}/eventos - sin sesion retorna 401")
    void crear_sin_sesion() throws Exception {
        mockMvc.perform(post("/api/v1/lanes/3/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Order received","tipoEvento":"INICIO","posicionX":0,"posicionY":0}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/eventos/{id} - detalle evento (200)")
    void detalle_evento() throws Exception {
        given(eventoService.obtener(1L, 1L)).willReturn(crearEvento(1L, "Order shipped", TipoEvento.FIN));

        mockMvc.perform(get("/api/v1/eventos/1").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Order shipped"))
                .andExpect(jsonPath("$.tipoEvento").value("FIN"));
    }

    @Test
    @DisplayName("GET /api/v1/eventos/{id} - sin sesion retorna 401")
    void detalle_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/eventos/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/lanes/{laneId}/eventos - listar eventos (200)")
    void listar_eventos() throws Exception {
        given(eventoService.listarPorLane(1L, 3L))
                .willReturn(List.of(crearEvento(1L, "Order received", TipoEvento.MENSAJE_INICIO)));

        mockMvc.perform(get("/api/v1/lanes/3/eventos").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Order received"));
    }

    @Test
    @DisplayName("PUT /api/v1/eventos/{id} - editar evento (200)")
    void editar_evento() throws Exception {
        given(eventoService.editar(eq(1L), eq(1L), eq(1L), anyString(), any(), anyInt(), anyInt(), eq(3L)))
                .willReturn(crearEvento(1L, "Order cancelled", TipoEvento.FIN));

        mockMvc.perform(put("/api/v1/eventos/1")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Order cancelled","tipoEvento":"FIN","posicionX":900,"posicionY":20,
                                 "version":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoEvento").value("FIN"));
    }

    @Test
    @DisplayName("PUT /api/v1/eventos/{id} - sin la version leida (400)")
    void editar_sin_version() throws Exception {
        mockMvc.perform(put("/api/v1/eventos/1")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Order cancelled","tipoEvento":"FIN","posicionX":900,"posicionY":20}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/eventos/{id} - eliminar evento (204)")
    void eliminar_evento() throws Exception {
        doNothing().when(eventoService).eliminar(1L, 1L, 1L);

        mockMvc.perform(delete("/api/v1/eventos/1").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
    }

    private EventoResponse crearEvento(Long id, String nombre, TipoEvento tipo) {
        return new EventoResponse(id, nombre, tipo, 20, 80, 3L, 0L, null, null, null, null);
    }
}
