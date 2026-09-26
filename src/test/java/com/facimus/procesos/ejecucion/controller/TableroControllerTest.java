package com.facimus.procesos.ejecucion.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.ejecucion.dto.response.CasosPorEstadoResponse;
import com.facimus.procesos.ejecucion.dto.response.CicloDeCasoResponse;
import com.facimus.procesos.ejecucion.dto.response.LoQueSalioMalResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajesPorEstadoResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajesPorResultadoResponse;
import com.facimus.procesos.ejecucion.dto.response.TableroResponse;
import com.facimus.procesos.ejecucion.dto.response.TareasPorRolResponse;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;
import com.facimus.procesos.ejecucion.service.TableroService;

@WebMvcTest(TableroController.class)
class TableroControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TableroService tableroService;

    @Test
    @DisplayName("GET /procesos/{id}/tablero - responde los numeros del proceso, para cualquier rol (200)")
    void tableroDelProceso_respondeSusNumeros() throws Exception {
        given(tableroService.de(1L, 10L)).willReturn(tablero(10L));

        mockMvc.perform(get("/api/v1/procesos/10/tablero").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procesoId").value(10))
                .andExpect(jsonPath("$.casos").value(20))
                .andExpect(jsonPath("$.casosPorEstado[0].estado").value("TERMINADO"))
                .andExpect(jsonPath("$.ciclo.medio").value(6.4))
                .andExpect(jsonPath("$.ciclo.p95").value(9))
                .andExpect(jsonPath("$.tareasPorRol[0].rolNombre").value("Warehouse"))
                .andExpect(jsonPath("$.salientes[0].estado").value("ENTREGADO"))
                .andExpect(jsonPath("$.entrantes[0].resultado").value("ENTREGADO_A_CASO"))
                .andExpect(jsonPath("$.loQueSalioMal.enviosFallidos").value(2));
    }

    @Test
    @DisplayName("GET /empresas/actual/tablero - los mismos numeros de toda la tienda, sin proceso (200)")
    void tableroDeLaTienda_respondeSinProceso() throws Exception {
        given(tableroService.de(1L, null)).willReturn(tablero(null));

        mockMvc.perform(get("/api/v1/empresas/actual/tablero").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procesoId").doesNotExist())
                .andExpect(jsonPath("$.casos").value(20));
    }

    private static TableroResponse tablero(Long procesoId) {
        return new TableroResponse(procesoId, 20,
                List.of(new CasosPorEstadoResponse(EstadoCaso.TERMINADO, 18),
                        new CasosPorEstadoResponse(EstadoCaso.ABIERTO, 2)),
                new CicloDeCasoResponse(18, 6.4, 9),
                List.of(new TareasPorRolResponse(2L, "Warehouse", 2)),
                List.of(new MensajesPorEstadoResponse(EstadoMensajeSaliente.ENTREGADO, 36)),
                List.of(new MensajesPorResultadoResponse(ResultadoCorrelacion.ENTREGADO_A_CASO, 36)),
                new LoQueSalioMalResponse(2, 1, 3));
    }
}
