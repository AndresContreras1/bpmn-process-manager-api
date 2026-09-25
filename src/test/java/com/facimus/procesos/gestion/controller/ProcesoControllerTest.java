package com.facimus.procesos.gestion.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.ProcesoDetalleResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.service.ProcesoService;
import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@WebMvcTest(ProcesoController.class)
class ProcesoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcesoService procesoService;

    @Test
    @DisplayName("GET /api/v1/procesos - listar procesos (200)")
    void listar_procesos() throws Exception {
        given(procesoService.buscar(eq(1L), any(), any(), any(), anyBoolean(), any()))
                .willReturn(new PageResponse<>(List.of(crearProceso(1L, "Ventas")), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/procesos").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Ventas"));
    }

    @Test
    @DisplayName("GET /api/v1/procesos?incluirInactivos - el administrador ve los eliminados y el editor no")
    void listar_incluirInactivos_soloParaElAdministrador() throws Exception {
        given(procesoService.buscar(eq(1L), isNull(), isNull(), isNull(), eq(true), any()))
                .willReturn(PageResponse.from(new PageImpl<>(List.of(crearProceso(9L, "Retirado")))));

        mockMvc.perform(get("/api/v1/procesos").param("incluirInactivos", "true")
                        .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(9));

        mockMvc.perform(get("/api/v1/procesos").param("incluirInactivos", "true")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud inválida"))
                .andExpect(jsonPath("$.detail")
                        .value("Solo un administrador puede consultar los procesos eliminados."));
    }

    @Test
    @DisplayName("GET /api/v1/procesos - por defecto 10 por pagina, los modificados mas recientes primero y el id desempata")
    void listar_procesos_ordenPorDefecto() throws Exception {
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        given(procesoService.buscar(eq(1L), isNull(), isNull(), isNull(), anyBoolean(), pagina.capture()))
                .willReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mockMvc.perform(get("/api/v1/procesos").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk());

        assertThat(pagina.getValue().getPageNumber()).isZero();
        assertThat(pagina.getValue().getPageSize()).isEqualTo(10);
        assertThat(pagina.getValue().getSort())
                .containsExactly(Sort.Order.desc("fechaModificacion"), Sort.Order.asc("id"));
    }

    @Test
    @DisplayName("GET /api/v1/procesos?pagina=2&tamano=25&orden=nombre,asc - el cliente elige tamano y orden")
    void listar_procesos_tamanoYOrdenElegidos() throws Exception {
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        given(procesoService.buscar(eq(1L), isNull(), isNull(), isNull(), anyBoolean(), pagina.capture()))
                .willReturn(new PageResponse<>(List.of(), 2, 25, 0, 0));

        mockMvc.perform(get("/api/v1/procesos").param("pagina", "2").param("tamano", "25")
                        .param("orden", "nombre,asc").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk());

        assertThat(pagina.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pagina.getValue().getPageSize()).isEqualTo(25);
        assertThat(pagina.getValue().getSort()).containsExactly(Sort.Order.asc("nombre"), Sort.Order.asc("id"));
    }

    @Test
    @DisplayName("GET /api/v1/procesos?orden=descripcion&tamano=0 - fuera de la lista blanca o del rango responde 400")
    void listar_procesos_parametrosInvalidos() throws Exception {
        mockMvc.perform(get("/api/v1/procesos").param("orden", "descripcion").param("tamano", "0")
                        .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.orden").exists())
                .andExpect(jsonPath("$.errors.tamano").value("El tamaño de página va de 1 a 50."));
    }

    @Test
    @DisplayName("GET /api/v1/procesos - sin sesion retorna 401")
    void listar_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/procesos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/procesos - crear proceso como editor (201)")
    void crear_proceso() throws Exception {
        ProcesoResponse p = crearProceso(2L, "Compras");
        given(procesoService.crear(eq(1L), eq(1L), anyString(), anyString(), anyString())).willReturn(p);

        mockMvc.perform(post("/api/v1/procesos")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Compras","descripcion":"Proceso de compras","categoria":"Operativo"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/procesos/2"))
                .andExpect(jsonPath("$.nombre").value("Compras"));
    }

    @Test
    @DisplayName("POST /api/v1/procesos - solo lectura retorna 403")
    void crear_proceso_solo_lectura() throws Exception {
        mockMvc.perform(post("/api/v1/procesos")
                        .with(principal(RolAcceso.SOLO_LECTURA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"X","descripcion":"Y","categoria":"Z"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/procesos/{id} - detalle proceso (200)")
    void detalle_proceso() throws Exception {
        given(procesoService.obtenerDetalle(1L, 1L, false))
                .willReturn(new ProcesoDetalleResponse(crearProceso(1L, "Ventas"), List.of()));

        mockMvc.perform(get("/api/v1/procesos/1").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proceso.nombre").value("Ventas"))
                .andExpect(jsonPath("$.historial").isArray());
    }

    @Test
    @DisplayName("PUT /api/v1/procesos/{id} - editar proceso (200)")
    void editar_proceso() throws Exception {
        ProcesoResponse p = crearProceso(1L, "Ventas v2");
        given(procesoService.editarDatos(eq(1L), eq(1L), eq(1L), anyString(), anyString(), anyString(), eq(3L)))
                .willReturn(p);

        mockMvc.perform(put("/api/v1/procesos/1")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ventas v2","descripcion":"Desc","categoria":"Op","version":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ventas v2"));
    }

    @Test
    @DisplayName("PATCH /api/v1/procesos/{id} - publicar un borrador (200)")
    void publicar_proceso() throws Exception {
        ProcesoResponse p = crearProceso(1L, "Ventas", EstadoProceso.PUBLICADO);
        given(procesoService.cambiarEstado(1L, 1L, 1L, EstadoProceso.PUBLICADO, 3L)).willReturn(p);
        mockMvc.perform(patch("/api/v1/procesos/1")
                .with(principal(RolAcceso.EDITOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"estado":"PUBLICADO","version":3}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"));
    }

    @Test
    void consultar_historial_separado() throws Exception {
        given(procesoService.listarHistorial(1L, 1L)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/procesos/1/historial").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("DELETE /api/v1/procesos/{id} - eliminar como admin (204)")
    void eliminar_proceso() throws Exception {
        doNothing().when(procesoService).eliminarLogico(1L, 1L, 1L);

        mockMvc.perform(delete("/api/v1/procesos/1").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/v1/procesos/{id} - editor no puede eliminar (403)")
    void eliminar_como_editor() throws Exception {
        mockMvc.perform(delete("/api/v1/procesos/1").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/procesos - pagina negativa retorna ProblemDetail 400")
    void listar_pagina_negativa() throws Exception {
        mockMvc.perform(get("/api/v1/procesos?pagina=-1").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.errors.pagina").value("La página no puede ser negativa."));
    }

    @Test
    @DisplayName("GET /api/v1/procesos - estado desconocido retorna ProblemDetail 400")
    void listar_estado_desconocido() throws Exception {
        mockMvc.perform(get("/api/v1/procesos?estado=DESCONOCIDO").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.errors.estado").value("Valor no permitido. Valores válidos: BORRADOR, PUBLICADO."));
    }

    @Test
    @DisplayName("POST /api/v1/procesos - JSON malformado retorna ProblemDetail 400")
    void crear_json_malformado() throws Exception {
        mockMvc.perform(post("/api/v1/procesos")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("JSON inválido"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/procesos - un campo que el contrato no tiene, como empresaId, retorna 400")
    void crear_campoDesconocido_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/procesos")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ventas","descripcion":"Proceso de ventas","categoria":"Comercial",
                                 "empresaId":2}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("JSON inválido"))
                .andExpect(jsonPath("$.errors.empresaId").value("El campo no existe en esta operación."));

        verify(procesoService, never()).crear(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("PATCH /api/v1/procesos/{id} - un estado que no existe retorna 400 con los valores validos")
    void cambiarEstado_estadoInexistente_devuelveValoresValidos() throws Exception {
        mockMvc.perform(patch("/api/v1/procesos/1")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ARCHIVADO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.estado").value("Valor no permitido. Valores válidos: BORRADOR, PUBLICADO."));
    }

    @Test
    @DisplayName("POST /api/v1/procesos - cada campo invalido llega con su mensaje en errors")
    void crear_camposVacios_devuelveErrorPorCampo() throws Exception {
        mockMvc.perform(post("/api/v1/procesos")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"descripcion\":\" \",\"categoria\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validación fallida"))
                .andExpect(jsonPath("$.detail").value("Uno o más campos no son válidos."))
                .andExpect(jsonPath("$.errors.nombre").value("El nombre es obligatorio."))
                .andExpect(jsonPath("$.errors.descripcion").value("La descripcion es obligatoria."))
                .andExpect(jsonPath("$.errors.categoria").value("La categoria es obligatoria."));
    }

    @Test
    @DisplayName("POST /api/v1/procesos - un nombre mas largo que su columna retorna 400")
    void crear_nombreDemasiadoLargo_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/procesos")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + "x".repeat(121)
                                + "\",\"descripcion\":\"Proceso de ventas\",\"categoria\":\"Comercial\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nombre").value("El nombre no puede superar 120 caracteres."));

        verify(procesoService, never()).crear(any(), any(), any(), any(), any());
    }

    private ProcesoResponse crearProceso(Long id, String nombre) {
        return crearProceso(id, nombre, EstadoProceso.BORRADOR);
    }

    private ProcesoResponse crearProceso(Long id, String nombre, EstadoProceso estado) {
        LocalDateTime ahora = LocalDateTime.now();
        return new ProcesoResponse(id, nombre, "Descripcion", "Operativo", estado, true, ahora, ahora, 0L, null, null,
                null, false);
    }

}
