package com.facimus.procesos.modelado.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.modelado.dto.response.ArtefactoResponse;
import com.facimus.procesos.modelado.dto.response.AsociacionResponse;
import com.facimus.procesos.modelado.dto.response.DiagnosticoResponse;
import com.facimus.procesos.modelado.model.DireccionAsociacion;
import com.facimus.procesos.modelado.model.TipoArtefacto;
import com.facimus.procesos.modelado.model.TipoAsociacion;
import com.facimus.procesos.modelado.service.ArtefactoService;
import com.facimus.procesos.modelado.service.AsociacionService;
import com.facimus.procesos.modelado.service.DatosDeArtefacto;
import com.facimus.procesos.modelado.service.DiagnosticoService;

/** PR 55: lo que la API dice de los artefactos, las asociaciones y el modo: validaciones, codigos y permisos. */
@WebMvcTest({ArtefactoController.class, AsociacionController.class, ModoProcesoController.class})
class ElementosDescriptivosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArtefactoService artefactoService;

    @MockitoBean
    private AsociacionService asociacionService;

    @MockitoBean
    private ProcesoService procesoService;

    @MockitoBean
    private DiagnosticoService diagnosticoService;

    @Test
    @DisplayName("POST /procesos/{id}/artefactos - crea el artefacto (201)")
    void crearArtefacto() throws Exception {
        given(artefactoService.crear(eq(1L), eq(1L), eq(7L), any(DatosDeArtefacto.class))).willReturn(artefacto());

        mockMvc.perform(post("/api/v1/procesos/7/artefactos")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"ANOTACION","texto":"Check by hand","laneId":3,"posicionX":10,"posicionY":20}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/artefactos/40"))
                .andExpect(jsonPath("$.tipo").value("ANOTACION"));
    }

    @Test
    @DisplayName("POST /procesos/{id}/artefactos - sin tipo o con tamano negativo (400)")
    void crearArtefacto_invalido() throws Exception {
        mockMvc.perform(post("/api/v1/procesos/7/artefactos")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texto":"x","posicionX":0,"posicionY":0}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/procesos/7/artefactos")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"GRUPO","posicionX":0,"posicionY":0,"ancho":-5}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /artefactos/{id} - sin version (400), con version (200)")
    void editarArtefacto() throws Exception {
        given(artefactoService.editar(eq(1L), eq(1L), eq(40L), any(DatosDeArtefacto.class), eq(2L)))
                .willReturn(artefacto());

        mockMvc.perform(put("/api/v1/artefactos/40")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texto":"x","posicionX":0,"posicionY":0}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/artefactos/40")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texto":"x","posicionX":0,"posicionY":0,"version":2}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET de artefactos y asociaciones (200)")
    void listar() throws Exception {
        given(artefactoService.listar(1L, 7L)).willReturn(List.of(artefacto()));
        given(artefactoService.obtener(1L, 40L)).willReturn(artefacto());
        given(asociacionService.listar(1L, 7L)).willReturn(List.of(asociacion()));

        mockMvc.perform(get("/api/v1/procesos/7/artefactos").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(40));
        mockMvc.perform(get("/api/v1/artefactos/40").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/procesos/7/asociaciones").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].origen").value("TextAnnotation_1"));
    }

    @Test
    @DisplayName("Borrar un artefacto o una asociacion es de un administrador")
    void borrar_soloAdministrador() throws Exception {
        mockMvc.perform(delete("/api/v1/artefactos/40").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/asociaciones/50").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(artefactoService, asociacionService);

        mockMvc.perform(delete("/api/v1/artefactos/40").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/asociaciones/50").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
        verify(artefactoService).eliminar(1L, 1L, 40L);
        verify(asociacionService).eliminar(1L, 1L, 50L);
    }

    @Test
    @DisplayName("POST /procesos/{id}/asociaciones - crea (201) y exige sus extremos (400)")
    void crearAsociacion() throws Exception {
        given(asociacionService.crear(1L, 1L, 7L, TipoAsociacion.ASOCIACION, "TextAnnotation_1", "Activity_2", null))
                .willReturn(asociacion());

        mockMvc.perform(post("/api/v1/procesos/7/asociaciones")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"ASOCIACION","origen":"TextAnnotation_1","destino":"Activity_2"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/procesos/7/asociaciones"));
        mockMvc.perform(post("/api/v1/procesos/7/asociaciones")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"ASOCIACION","origen":"","destino":"Activity_2"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /procesos/{id}/modo - responde el proceso y el diagnostico del modo nuevo")
    void cambiarModo() throws Exception {
        given(procesoService.cambiarModo(1L, 7L, 1L, ModoProceso.DOCUMENTAL, 3L)).willReturn(proceso());
        given(diagnosticoService.diagnosticar(eq(1L), eq(7L), isNull()))
                .willReturn(new DiagnosticoResponse(7L, null, 0, 0, List.of()));

        mockMvc.perform(put("/api/v1/procesos/7/modo")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modo":"DOCUMENTAL","version":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proceso.modo").value("DOCUMENTAL"))
                .andExpect(jsonPath("$.diagnostico.errores").value(0));
    }

    @Test
    @DisplayName("PUT /procesos/{id}/modo - un modo que no existe o sin version (400)")
    void cambiarModo_invalido() throws Exception {
        mockMvc.perform(put("/api/v1/procesos/7/modo")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modo":"A_MEDIAS","version":3}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/procesos/7/modo")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"modo":"DOCUMENTAL"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private static ArtefactoResponse artefacto() {
        return new ArtefactoResponse(40L, "TextAnnotation_1", TipoArtefacto.ANOTACION, "Check by hand", 2L, 3L, 10, 20,
                100, 30, 0L, null, null, null, null);
    }

    private static AsociacionResponse asociacion() {
        return new AsociacionResponse(50L, "Association_1", TipoAsociacion.ASOCIACION, "TextAnnotation_1", "Activity_2",
                DireccionAsociacion.NINGUNA, 0L, null, null, null, null);
    }

    private static ProcesoResponse proceso() {
        return new ProcesoResponse(7L, "Order", "Desc", "Cat", EstadoProceso.BORRADOR, true, null, null, 4L, null, null,
                null, false, ModoProceso.DOCUMENTAL);
    }
}
