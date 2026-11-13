package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Types;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.service.Actor;
import com.facimus.procesos.gestion.service.Permisos;
import com.facimus.procesos.gestion.service.Visibilidad;
import com.facimus.procesos.modelado.dto.response.ResultadoDeBusquedaResponse;
import com.facimus.procesos.modelado.dto.response.ResultadoDeBusquedaResponse.Segmento;
import com.facimus.procesos.modelado.model.TipoDeResultado;
import com.facimus.procesos.modelado.service.impl.BusquedaServiceImpl.Acierto;
import com.facimus.procesos.modelado.service.impl.BusquedaServiceImpl.Fila;

/** PR 65: la busqueda arma sus dos consultas con lo visible, conserva el orden y parte el fragmento en tramos. */
@ExtendWith(MockitoExtension.class)
class BusquedaServiceImplTest {

    private static final Long EMPRESA = 1L;
    private static final Actor CARLA = new Actor(9L, EMPRESA, RolAcceso.SOLO_LECTURA);
    private static final String MARCADO = "Aprobar el \uE000crédito\uE001 del cliente";

    @Mock
    private NamedParameterJdbcTemplate jdbc;
    @Mock
    private Permisos permisos;
    @InjectMocks
    private BusquedaServiceImpl servicio;

    private void aciertos(List<Acierto> aciertos) {
        when(jdbc.query(eq(BusquedaServiceImpl.PAGINA), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<Acierto>>any())).thenReturn(aciertos);
    }

    private void filas(List<Fila> filas) {
        when(jdbc.query(eq(BusquedaServiceImpl.DETALLES), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<Fila>>any())).thenReturn(filas);
    }

    private MapSqlParameterSource parametrosDe(String sql) {
        ArgumentCaptor<SqlParameterSource> parametros = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(eq(sql), parametros.capture(), ArgumentMatchers.<RowMapper<?>>any());
        return (MapSqlParameterSource) parametros.getValue();
    }

    private static Fila fila(TipoDeResultado tipo, long id) {
        return new Fila(tipo.name(), id, 3L, "Despacho", "Titulo " + id, null, null, null, null, null, null, MARCADO);
    }

    @Test
    @DisplayName("un texto vacio, en blanco o que no llega no consulta nada y responde una pagina vacia")
    void vacio() {
        for (String texto : new String[] {null, "", "   "}) {
            PageResponse<ResultadoDeBusquedaResponse> pagina = servicio.buscar(CARLA, texto, null,
                    false, PageRequest.of(2, 15));

            assertThat(pagina.content()).isEmpty();
            assertThat(pagina.totalElements()).isZero();
            assertThat(pagina.page()).isEqualTo(2);
            assertThat(pagina.size()).isEqualTo(15);
        }
        verifyNoInteractions(jdbc, permisos);
    }

    @Test
    @DisplayName("lo visible del evaluador de permisos va a la misma consulta, con el texto sin espacios y la pagina")
    void parametros() {
        when(permisos.visibilidad(CARLA)).thenReturn(new Visibilidad(false, Set.of(5L, 3L), Set.of(), Set.of(9L)));
        aciertos(List.of());

        servicio.buscar(CARLA, "  crédito  ", null, false, PageRequest.of(2, 20));

        MapSqlParameterSource parametros = parametrosDe(BusquedaServiceImpl.PAGINA);
        assertThat(parametros.getValue("empresa")).isEqualTo(EMPRESA);
        assertThat(parametros.getValue("texto")).isEqualTo("crédito");
        assertThat(parametros.getValue("tipo")).isNull();
        assertThat(parametros.getSqlType("tipo")).isEqualTo(Types.VARCHAR);
        assertThat(parametros.getValue("carpetasOcultas")).isEqualTo("{3,5}");
        assertThat(parametros.getValue("procesosVisibles")).isEqualTo("{9}");
        assertThat(parametros.getValue("procesosOcultos")).isEqualTo("{}");
        assertThat(parametros.getValue("tamano")).isEqualTo(20);
        assertThat(parametros.getValue("desde")).isEqualTo(40L);
        assertThat(parametros.getValue("publicado")).isEqualTo(false);
        assertThat(parametros.getSqlType("publicado")).isEqualTo(Types.BOOLEAN);
    }

    @Test
    @DisplayName("solo lo publicado: la consulta lo recibe, para quedarse con la version vigente sin comentarios")
    void soloPublicado() {
        when(permisos.visibilidad(CARLA)).thenReturn(Visibilidad.TODO);
        aciertos(List.of());

        servicio.buscar(CARLA, "credito", null, true, PageRequest.of(0, 20));

        assertThat(parametrosDe(BusquedaServiceImpl.PAGINA).getValue("publicado")).isEqualTo(true);
    }

    @Test
    @DisplayName("con tipo, la consulta lo recibe por su nombre")
    void tipo() {
        when(permisos.visibilidad(CARLA)).thenReturn(Visibilidad.TODO);
        aciertos(List.of());

        servicio.buscar(CARLA, "credito", TipoDeResultado.ELEMENTO, false, PageRequest.of(0, 20));

        assertThat(parametrosDe(BusquedaServiceImpl.PAGINA).getValue("tipo")).isEqualTo("ELEMENTO");
    }

    @Test
    @DisplayName("lo que se muestra se pide de una vez para la pagina, y sale en el orden de relevancia")
    void orden() {
        when(permisos.visibilidad(CARLA)).thenReturn(Visibilidad.TODO);
        aciertos(List.of(new Acierto("COMENTARIO", 7L, 23L), new Acierto("PROCESO", 2L, 23L),
                new Acierto("ELEMENTO", 5L, 23L), new Acierto("PROCESO", 4L, 23L)));
        filas(List.of(fila(TipoDeResultado.PROCESO, 4), fila(TipoDeResultado.PROCESO, 2),
                fila(TipoDeResultado.ELEMENTO, 5), fila(TipoDeResultado.COMENTARIO, 7)));

        PageResponse<ResultadoDeBusquedaResponse> pagina = servicio.buscar(CARLA, "credito", null,
                false, PageRequest.of(0, 4));

        assertThat(pagina.content()).extracting(ResultadoDeBusquedaResponse::tipo, ResultadoDeBusquedaResponse::titulo)
                .containsExactly(tuple(TipoDeResultado.COMENTARIO, "Titulo 7"),
                        tuple(TipoDeResultado.PROCESO, "Titulo 2"),
                        tuple(TipoDeResultado.ELEMENTO, "Titulo 5"),
                        tuple(TipoDeResultado.PROCESO, "Titulo 4"));
        assertThat(pagina.totalElements()).isEqualTo(23);
        assertThat(pagina.totalPages()).isEqualTo(6);
        MapSqlParameterSource detalles = parametrosDe(BusquedaServiceImpl.DETALLES);
        assertThat(detalles.getValue("empresa")).isEqualTo(EMPRESA);
        assertThat(detalles.getValue("texto")).isEqualTo("credito");
        assertThat(detalles.getValue("procesos")).isEqualTo("{2,4}");
        assertThat(detalles.getValue("elementos")).isEqualTo("{5}");
        assertThat(detalles.getValue("documentos")).isEqualTo("{}");
        assertThat(detalles.getValue("comentarios")).isEqualTo("{7}");
        assertThat(detalles.getValue("marcas")).isEqualTo("\uE000\uE001");
        assertThat(detalles.getValue("opciones")).isEqualTo(BusquedaServiceImpl.OPCIONES);
        assertThat(detalles.getValue("largoDelFragmento")).isEqualTo(20_000);
        verify(jdbc, never()).queryForObject(eq(BusquedaServiceImpl.CONTEO), any(SqlParameterSource.class),
                eq(Long.class));
    }

    @Test
    @DisplayName("lo que se borro entre las dos consultas no sale, y el resto sigue en su orden")
    void borradoEnElCamino() {
        when(permisos.visibilidad(CARLA)).thenReturn(Visibilidad.TODO);
        aciertos(List.of(new Acierto("DOCUMENTO", 8L, 2L), new Acierto("PROCESO", 2L, 2L)));
        filas(List.of(fila(TipoDeResultado.PROCESO, 2)));

        assertThat(servicio.buscar(CARLA, "credito", null, false, PageRequest.of(0, 20)).content())
                .extracting(ResultadoDeBusquedaResponse::tipo).containsExactly(TipoDeResultado.PROCESO);
    }

    @Test
    @DisplayName("una primera pagina sin nada no cuenta ni pide detalles: no hay nada")
    void nadaEnLaPrimera() {
        when(permisos.visibilidad(CARLA)).thenReturn(Visibilidad.TODO);
        aciertos(List.of());

        PageResponse<ResultadoDeBusquedaResponse> pagina = servicio.buscar(CARLA, "credito", null,
                false, PageRequest.of(0, 20));

        assertThat(pagina.content()).isEmpty();
        assertThat(pagina.totalElements()).isZero();
        verify(jdbc, never()).queryForObject(eq(BusquedaServiceImpl.CONTEO), any(SqlParameterSource.class),
                eq(Long.class));
        verify(jdbc, never()).query(eq(BusquedaServiceImpl.DETALLES), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<Fila>>any());
    }

    @Test
    @DisplayName("una pagina despues de la ultima cuenta cuantos hay, con lo mismo que la pagina")
    void despuesDeLaUltima() {
        when(permisos.visibilidad(CARLA)).thenReturn(Visibilidad.TODO);
        aciertos(List.of());
        ArgumentCaptor<SqlParameterSource> parametros = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(jdbc.queryForObject(eq(BusquedaServiceImpl.CONTEO), parametros.capture(), eq(Long.class)))
                .thenReturn(7L, (Long) null);

        PageResponse<ResultadoDeBusquedaResponse> pagina = servicio.buscar(CARLA, "credito", null,
                false, PageRequest.of(3, 5));
        PageResponse<ResultadoDeBusquedaResponse> sinConteo = servicio.buscar(CARLA, "credito", null,
                false, PageRequest.of(3, 5));

        assertThat(pagina.content()).isEmpty();
        assertThat(pagina.totalElements()).isEqualTo(7);
        assertThat(sinConteo.totalElements()).isZero();
        assertThat(((MapSqlParameterSource) parametros.getValue()).getValue("texto")).isEqualTo("credito");
    }

    @Test
    @DisplayName("cada campo de la fila llega a la respuesta, con el fragmento en tramos")
    void respuesta() {
        ResultadoDeBusquedaResponse respuesta = BusquedaServiceImpl.respuesta(new Fila("COMENTARIO", 7L, 3L,
                "Despacho", "Ana", "Activity_1", "ACTIVIDAD", 2, 40L, 7L, 6L, MARCADO));

        assertThat(respuesta).isEqualTo(new ResultadoDeBusquedaResponse(TipoDeResultado.COMENTARIO, 3L, "Despacho",
                "Ana", "Activity_1", "ACTIVIDAD", 2, 40L, 7L, 6L, List.of(new Segmento("Aprobar el ", false),
                        new Segmento("crédito", true), new Segmento(" del cliente", false))));
    }

    @Test
    @DisplayName("el fragmento se parte por las marcas: sin tramos vacios, y lo que no se marco es texto plano")
    void segmentos() {
        assertThat(BusquedaServiceImpl.segmentos(null)).isEmpty();
        assertThat(BusquedaServiceImpl.segmentos("")).isEmpty();
        assertThat(BusquedaServiceImpl.segmentos("<b>sin</b> & marcas"))
                .containsExactly(new Segmento("<b>sin</b> & marcas", false));
        assertThat(BusquedaServiceImpl.segmentos("\uE000uno\uE001\uE000dos\uE001 y \uE000tres\uE001"))
                .containsExactly(new Segmento("uno", true), new Segmento("dos", true), new Segmento(" y ", false),
                        new Segmento("tres", true));
    }

    @Test
    @DisplayName("los ids van a PostgreSQL como un arreglo, en orden")
    void arreglo() {
        assertThat(BusquedaServiceImpl.arreglo(List.of())).isEqualTo("{}");
        assertThat(BusquedaServiceImpl.arreglo(Set.of(10L, 2L, 1L))).isEqualTo("{1,2,10}");
    }

    @Test
    @DisplayName("las consultas usan websearch_to_tsquery, nunca to_tsquery, y ts_headline marca con las dos marcas")
    void consultas() {
        assertThat(BusquedaServiceImpl.PAGINA).contains("websearch_to_tsquery").doesNotContain(" to_tsquery(")
                .doesNotContain("(to_tsquery(");
        assertThat(BusquedaServiceImpl.OPCIONES).isEqualTo("StartSel=\uE000, StopSel=\uE001, MaxWords=35, MinWords=15, "
                + "MaxFragments=2, FragmentDelimiter=\" … \"");
        // Lo buscado se arma una vez, y el fragmento de un documento sale solo del principio de su texto
        assertThat(BusquedaServiceImpl.PAGINA).startsWith("with buscado as materialized")
                .containsOnlyOnce("websearch_to_tsquery");
        assertThat(BusquedaServiceImpl.DETALLES).startsWith("with buscado as materialized")
                .containsOnlyOnce("websearch_to_tsquery").contains("left(a.texto, :largoDelFragmento)");

        assertThat(BusquedaServiceImpl.MARCAS)
                .isEqualTo(String.valueOf(new char[] {BusquedaServiceImpl.INICIO, BusquedaServiceImpl.FIN}));
    }
}
