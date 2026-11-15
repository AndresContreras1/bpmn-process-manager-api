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
import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.ejecucion.dto.response.CargaDeTrabajoResponse;
import com.facimus.procesos.ejecucion.dto.response.CargaPorPersonaResponse;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.model.Bandeja;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.service.AsignacionDeTareaService;
import com.facimus.procesos.ejecucion.service.TareaService;

@WebMvcTest(TareaController.class)
class TareaControllerTest {

    private static final LocalDateTime CREADA = LocalDateTime.of(2026, 9, 24, 15, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TareaService tareaService;

    @MockitoBean
    private AsignacionDeTareaService asignacionDeTareaService;

    @Test
    @DisplayName("GET /api/v1/tareas - la bandeja de la tienda para cualquier rol (200)")
    void bandeja_devuelveLaPagina() throws Exception {
        given(tareaService.bandeja(eq(1L), eq(1L), eq(Bandeja.TODAS), eq(null), eq(null), eq(null),
                any(Pageable.class)))
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
        given(tareaService.bandeja(eq(1L), eq(1L), eq(Bandeja.TODAS), eq(2L), eq(10L),
                eq(EstadoActividadCaso.COMPLETADA), any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get("/api/v1/tareas")
                        .param("rolProcesoId", "2").param("procesoId", "10").param("estado", "COMPLETADA")
                        .with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk());

        verify(tareaService).bandeja(eq(1L), eq(1L), eq(Bandeja.TODAS), eq(2L), eq(10L),
                eq(EstadoActividadCaso.COMPLETADA), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/tareas?mias=true - la bandeja propia llega al service como tal (200)")
    void bandeja_mias_llegaAlService() throws Exception {
        given(tareaService.bandeja(eq(1L), eq(1L), eq(Bandeja.DE_MIS_ROLES), eq(null), eq(null), eq(null),
                any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(tarea(EstadoActividadCaso.EN_ESPERA)), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/tareas").param("mias", "true").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(77));

        verify(tareaService).bandeja(eq(1L), eq(1L), eq(Bandeja.DE_MIS_ROLES), eq(null), eq(null), eq(null),
                any(Pageable.class));
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
    @DisplayName("PR 70: GET /api/v1/tareas?bandeja=... - cada bandeja llega al service, y gana a mias (200)")
    void bandeja_porNombre_llegaAlService() throws Exception {
        given(tareaService.bandeja(eq(1L), eq(1L), any(Bandeja.class), eq(null), eq(null), eq(null),
                any(Pageable.class))).willReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get("/api/v1/tareas").param("bandeja", "MIAS").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/tareas").param("bandeja", "SIN_ASIGNAR").param("mias", "true")
                .with(principal(RolAcceso.SOLO_LECTURA))).andExpect(status().isOk());

        verify(tareaService).bandeja(eq(1L), eq(1L), eq(Bandeja.MIAS), eq(null), eq(null), eq(null),
                any(Pageable.class));
        verify(tareaService).bandeja(eq(1L), eq(1L), eq(Bandeja.SIN_ASIGNAR), eq(null), eq(null), eq(null),
                any(Pageable.class));
    }

    @Test
    @DisplayName("PR 70: GET /api/v1/tareas?bandeja=OTRA - una bandeja que no existe es invalida (400)")
    void bandeja_queNoExiste_esInvalida() throws Exception {
        mockMvc.perform(get("/api/v1/tareas").param("bandeja", "OTRA").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tareaService);
    }

    @Test
    @DisplayName("PR 70: GET /api/v1/tareas/carga - la carga por persona, de la tienda o de un proceso (200)")
    void carga_devuelveLaCarga() throws Exception {
        given(tareaService.carga(1L, 10L)).willReturn(new CargaDeTrabajoResponse(3,
                List.of(new CargaPorPersonaResponse(5L, "Luis Gomez", 4))));

        mockMvc.perform(get("/api/v1/tareas/carga").param("procesoId", "10").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinAsignar").value(3))
                .andExpect(jsonPath("$.personas[0].nombre").value("Luis Gomez"))
                .andExpect(jsonPath("$.personas[0].tareas").value(4));
    }

    @Test
    @DisplayName("PR 70: POST /api/v1/tareas/{id}/reclamar - la toma quien pregunta (200)")
    void reclamar_laToma() throws Exception {
        given(asignacionDeTareaService.reclamar(1L, 1L, 77L)).willReturn(tarea(EstadoActividadCaso.EN_ESPERA));

        mockMvc.perform(post("/api/v1/tareas/77/reclamar").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(77));

        verify(asignacionDeTareaService).reclamar(1L, 1L, 77L);
    }

    @Test
    @DisplayName("PR 70: POST /api/v1/tareas/{id}/reclamar - la que ya tiene alguien es un conflicto (409)")
    void reclamar_yaTomada_esConflicto() throws Exception {
        given(asignacionDeTareaService.reclamar(1L, 1L, 77L))
                .willThrow(new ReglaNegocioException("La tarea ya la tiene alguien."));

        mockMvc.perform(post("/api/v1/tareas/77/reclamar").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La tarea ya la tiene alguien."));
    }

    @Test
    @DisplayName("PR 70: POST /api/v1/tareas/{id}/reclamar - quien no es de su rol no la toma (403)")
    void reclamar_deOtroRol_sinPermiso() throws Exception {
        given(asignacionDeTareaService.reclamar(1L, 1L, 77L))
                .willThrow(new SinPermisoException("No perteneces a su rol."));

        mockMvc.perform(post("/api/v1/tareas/77/reclamar").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PR 70: POST /api/v1/tareas/{id}/soltar - con motivo o sin cuerpo (200)")
    void soltar_conYSinMotivo() throws Exception {
        given(asignacionDeTareaService.soltar(eq(1L), eq(1L), eq(77L), any()))
                .willReturn(tarea(EstadoActividadCaso.EN_ESPERA));

        mockMvc.perform(post("/api/v1/tareas/77/soltar")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Sin respuesta\"}")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/tareas/77/soltar").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk());

        verify(asignacionDeTareaService).soltar(1L, 1L, 77L, "Sin respuesta");
        verify(asignacionDeTareaService).soltar(1L, 1L, 77L, null);
    }

    @Test
    @DisplayName("PR 70: POST /api/v1/tareas/{id}/soltar - un motivo de mas de 500 caracteres no llega (400)")
    void soltar_motivoLargo_esInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/tareas/77/soltar")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"" + "x".repeat(501) + "\"}")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(asignacionDeTareaService);
    }

    @Test
    @DisplayName("PR 70: POST /api/v1/tareas/{id}/reasignar - pasa a otra persona con su motivo (200)")
    void reasignar_conMotivo() throws Exception {
        given(asignacionDeTareaService.reasignar(1L, 1L, 77L, 5L, "Ana is on leave"))
                .willReturn(tarea(EstadoActividadCaso.EN_ESPERA));

        mockMvc.perform(post("/api/v1/tareas/77/reasignar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":5,\"motivo\":\"Ana is on leave\"}")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk());

        verify(asignacionDeTareaService).reasignar(1L, 1L, 77L, 5L, "Ana is on leave");
    }

    @Test
    @DisplayName("PR 70: POST /api/v1/tareas/{id}/reasignar - sin persona ni motivo no llega al service (400)")
    void reasignar_sinPersonaNiMotivo_esInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/tareas/77/reasignar")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"  \"}")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.usuarioId").value("La persona es obligatoria."))
                .andExpect(jsonPath("$.errors.motivo").value("El motivo es obligatorio."));

        verifyNoInteractions(asignacionDeTareaService);
    }

    @Test
    @DisplayName("PR 70: POST /api/v1/tareas/{id}/delegar - la delega con su motivo (200)")
    void delegar_conMotivo() throws Exception {
        given(asignacionDeTareaService.delegar(1L, 1L, 77L, 5L, "Covering for me"))
                .willReturn(tarea(EstadoActividadCaso.EN_ESPERA));

        mockMvc.perform(post("/api/v1/tareas/77/delegar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":5,\"motivo\":\"Covering for me\"}")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk());

        verify(asignacionDeTareaService).delegar(1L, 1L, 77L, 5L, "Covering for me");
    }

    @Test
    @DisplayName("PR 70: POST /api/v1/tareas/{id}/delegar - sin motivo no llega al service (400)")
    void delegar_sinMotivo_esInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/tareas/77/delegar")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"usuarioId\":5}")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(asignacionDeTareaService);
    }

    private static TareaResponse tarea(EstadoActividadCaso estado) {
        return new TareaResponse(77L, 42L, "ORD-1001", 10L, "Order fulfillment", 12L, "Pick and pack items", 2L,
                estado, null, 0, null, null, 0L, CREADA);
    }
}
