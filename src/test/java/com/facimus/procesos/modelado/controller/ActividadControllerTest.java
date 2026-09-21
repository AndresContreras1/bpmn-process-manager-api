package com.facimus.procesos.modelado.controller;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.service.ActividadService;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(ActividadController.class)
class ActividadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActividadService actividadService;

    @Test
    @DisplayName("POST /api/v1/lanes/{laneId}/actividades - crear actividad (201)")
    void crear_actividad() throws Exception {
        Actividad a = crearActividad(1L, "Revisar solicitud");
        given(actividadService.crear(eq(1L), eq(3L), anyString(), anyString(), anyInt(), anyInt())).willReturn(a);

        mockMvc.perform(post("/api/v1/lanes/3/actividades")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Revisar solicitud","descripcion":"Verifica datos","posicionX":100,"posicionY":200}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/actividades/1"))
                .andExpect(jsonPath("$.nombre").value("Revisar solicitud"))
                .andExpect(jsonPath("$.posicionX").value(100));
    }

    @Test
    @DisplayName("POST /api/v1/lanes/{laneId}/actividades - sin sesion retorna 401")
    void crear_sin_sesion() throws Exception {
        mockMvc.perform(post("/api/v1/lanes/3/actividades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"X","descripcion":"Y","posicionX":0,"posicionY":0}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/lanes/{laneId}/actividades - validacion falla (400)")
    void crear_validacion_falla() throws Exception {
        mockMvc.perform(post("/api/v1/lanes/3/actividades")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"","descripcion":"","posicionX":0,"posicionY":0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/actividades/{id} - detalle actividad (200)")
    void detalle_actividad() throws Exception {
        Actividad a = crearActividad(1L, "Revisar solicitud");
        given(actividadService.obtener(1L, 1L)).willReturn(a);

        mockMvc.perform(get("/api/v1/actividades/1").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Revisar solicitud"));
    }

    @Test
    @DisplayName("GET /api/v1/actividades/{id} - sin sesion retorna 401")
    void detalle_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/actividades/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/lanes/{laneId}/actividades - listar actividades (200)")
    void listar_actividades() throws Exception {
        Actividad a = crearActividad(1L, "Revisar solicitud");
        given(actividadService.listarPorLane(1L, 3L)).willReturn(List.of(a));

        mockMvc.perform(get("/api/v1/lanes/3/actividades").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Revisar solicitud"));
    }

    @Test
    @DisplayName("GET /api/v1/lanes/{laneId}/actividades - sin sesion retorna 401")
    void listar_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/lanes/3/actividades"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/v1/actividades/{id} - editar actividad (200)")
    void editar_actividad() throws Exception {
        Actividad a = crearActividad(1L, "Revisar v2");
        given(actividadService.editar(eq(1L), eq(1L), anyString(), anyString(), anyInt(), anyInt())).willReturn(a);

        mockMvc.perform(put("/api/v1/actividades/1")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Revisar v2","descripcion":"Actualizada","posicionX":150,"posicionY":250}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Revisar v2"));
    }

    @Test
    @DisplayName("DELETE /api/v1/actividades/{id} - eliminar actividad (204)")
    void eliminar_actividad() throws Exception {
        doNothing().when(actividadService).eliminar(1L, 1L);
        mockMvc.perform(delete("/api/v1/actividades/1").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
    }

    private Actividad crearActividad(Long id, String nombre) {
        Lane lane = new Lane();
        lane.setId(3L);

        Actividad a = new Actividad();
        a.setId(id);
        a.setNombre(nombre);
        a.setDescripcion("Desc");
        a.setPosicionX(100);
        a.setPosicionY(200);
        a.setLane(lane);
        return a;
    }

}
