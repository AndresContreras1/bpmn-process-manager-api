package com.facimus.procesos.ejecucion.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.service.TareaService;

@WebMvcTest(TareaController.class)
class TareaControllerTest {

    private static final LocalDateTime CREADA = LocalDateTime.of(2026, 9, 24, 15, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TareaService tareaService;

    @Test
    @DisplayName("GET /api/v1/tareas - la bandeja de la tienda para cualquier rol (200)")
    void bandeja_devuelveLaPagina() throws Exception {
        given(tareaService.bandeja(eq(1L), eq(1L), eq(false), eq(null), eq(null), eq(null), any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(tarea(EstadoActividadCaso.EN_ESPERA)), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/tareas").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(77))
                .andExpect(jsonPath("$.content[0].nodoNombre").value("Pick and pack items"))
                .andExpect(jsonPath("$.content[0].casoReferencia").value("ORD-1001"))
                .andExpect(jsonPath("$.content[0].procesoNombre").value("Order fulfillment"));
    }

    @Test
    @DisplayName("GET /api/v1/tareas - los filtros de rol, proceso y estado llegan al service (200)")
    void bandeja_pasaLosFiltros() throws Exception {
        given(tareaService.bandeja(eq(1L), eq(1L), eq(false), eq(2L), eq(10L), eq(EstadoActividadCaso.COMPLETADA),
                any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get("/api/v1/tareas")
                        .param("rolProcesoId", "2").param("procesoId", "10").param("estado", "COMPLETADA")
                        .with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk());

        verify(tareaService).bandeja(eq(1L), eq(1L), eq(false), eq(2L), eq(10L), eq(EstadoActividadCaso.COMPLETADA),
                any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/tareas?mias=true - la bandeja propia llega al service como tal (200)")
    void bandeja_mias_llegaAlService() throws Exception {
        given(tareaService.bandeja(eq(1L), eq(1L), eq(true), eq(null), eq(null), eq(null), any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(tarea(EstadoActividadCaso.EN_ESPERA)), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/tareas").param("mias", "true").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(77));

        verify(tareaService).bandeja(eq(1L), eq(1L), eq(true), eq(null), eq(null), eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/tareas - una pagina mas grande que el tope no llega al service (400)")
    void bandeja_conTamanoExcesivo_esInvalida() throws Exception {
        mockMvc.perform(get("/api/v1/tareas").param("tamano", "51").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tareaService);
    }

    @Test
    @DisplayName("GET /api/v1/tareas/{id} - una tarea con su caso y su proceso (200)")
    void obtener_devuelveLaTarea() throws Exception {
        given(tareaService.obtener(1L, 77L)).willReturn(tarea(EstadoActividadCaso.EN_ESPERA));

        mockMvc.perform(get("/api/v1/tareas/77").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.casoId").value(42))
                .andExpect(jsonPath("$.rolProcesoId").value(2))
                .andExpect(jsonPath("$.estado").value("EN_ESPERA"));
    }

    @Test
    @DisplayName("GET /api/v1/tareas/{id} - un paso que no es una tarea no existe (404)")
    void obtener_queNoEsTarea_noExiste() throws Exception {
        given(tareaService.obtener(1L, 99L)).willThrow(new RecursoNoEncontradoException("Tarea no encontrada."));

        mockMvc.perform(get("/api/v1/tareas/99").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Tarea no encontrada."));
    }

    @Test
    @DisplayName("POST /api/v1/tareas/{id}/completar - completa la tarea con sus datos (200)")
    void completar_devuelveLaTareaCompletada() throws Exception {
        given(tareaService.completar(eq(1L), eq(1L), eq(77L), any()))
                .willReturn(tarea(EstadoActividadCaso.COMPLETADA));

        mockMvc.perform(post("/api/v1/tareas/77/completar")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"datos\":{\"packedItems\":3}}")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADA"));

        verify(tareaService).completar(1L, 1L, 77L, Map.of("packedItems", 3));
    }

    @Test
    @DisplayName("POST /api/v1/tareas/{id}/completar - sin datos tambien se completa (200)")
    void completar_sinDatos_seCompleta() throws Exception {
        given(tareaService.completar(1L, 1L, 77L, null)).willReturn(tarea(EstadoActividadCaso.COMPLETADA));

        mockMvc.perform(post("/api/v1/tareas/77/completar")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/tareas/{id}/completar - completarla dos veces es un conflicto (409)")
    void completar_dosVeces_esConflicto() throws Exception {
        given(tareaService.completar(eq(1L), eq(1L), eq(77L), any()))
                .willThrow(new ReglaNegocioException("La tarea ya fue completada."));

        mockMvc.perform(post("/api/v1/tareas/77/completar")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La tarea ya fue completada."));
    }

    @Test
    @DisplayName("POST /api/v1/tareas/{id}/asignar - reserva la tarea para alguien (200)")
    void asignar_reservaLaTarea() throws Exception {
        given(tareaService.asignar(1L, 77L, 5L)).willReturn(tarea(EstadoActividadCaso.EN_ESPERA));

        mockMvc.perform(post("/api/v1/tareas/77/asignar")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"usuarioId\":5}")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk());

        verify(tareaService).asignar(1L, 77L, 5L);
    }

    @Test
    @DisplayName("POST /api/v1/tareas/{id}/asignar - sin usuario la deja libre otra vez (200)")
    void asignar_sinUsuario_laLibera() throws Exception {
        given(tareaService.asignar(1L, 77L, null)).willReturn(tarea(EstadoActividadCaso.EN_ESPERA));

        mockMvc.perform(post("/api/v1/tareas/77/asignar")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk());

        verify(tareaService).asignar(1L, 77L, null);
    }

    private static TareaResponse tarea(EstadoActividadCaso estado) {
        return new TareaResponse(77L, 42L, "ORD-1001", 10L, "Order fulfillment", 12L, "Pick and pack items", 2L,
                estado, null, 0, null, null, 0L, CREADA);
    }
}
