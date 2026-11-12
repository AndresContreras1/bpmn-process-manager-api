package com.facimus.procesos.modelado.service.impl;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.service.Actor;
import com.facimus.procesos.gestion.service.Permisos;
import com.facimus.procesos.gestion.service.Visibilidad;
import com.facimus.procesos.modelado.dto.response.ResultadoDeBusquedaResponse;
import com.facimus.procesos.modelado.dto.response.ResultadoDeBusquedaResponse.Segmento;
import com.facimus.procesos.modelado.model.TipoDeResultado;
import com.facimus.procesos.modelado.service.BusquedaService;

import lombok.RequiredArgsConstructor;

/**
 * PR 65: la busqueda, en dos consultas. La primera ordena lo que encuentra el texto entre lo que la persona puede ver
 * (la tienda la pone la seguridad por filas, y lo visible lo dice el evaluador de permisos de una vez, como en la lista
 * de procesos) y se queda con la pagina; la segunda trae de esa pagina lo que hace falta mostrar, con su fragmento
 * resaltado. Ninguna escribe: los vectores los mantiene la base (V56).
 * <p>El texto va por {@code websearch_to_tsquery}, que entiende comillas, {@code -} y {@code or} y nunca falla por
 * sintaxis; nunca por {@code to_tsquery}. El orden es {@code ts_rank_cd} normalizado por el largo: un nombre gana a un
 * cuerpo, y un archivo largo no gana solo por largo.
 * <p>El fragmento lo arma {@code ts_headline} con dos marcas de uso privado de Unicode que se quitan antes del texto,
 * asi que no pueden venir de el; aqui se parte por ellas en tramos de texto plano. Nada del texto se vuelve HTML.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusquedaServiceImpl implements BusquedaService {

    /** Donde empieza y donde termina una palabra resaltada en lo que devuelve ts_headline. */
    static final char INICIO = '';
    static final char FIN = '';
    static final String MARCAS = "";
    static final String OPCIONES = "StartSel=, StopSel=, MaxWords=35, MinWords=15, MaxFragments=2, "
            + "FragmentDelimiter=\" … \"";

    private static final String CON_LO_VISIBLE = """
            with visibles as (
                select p.id, p.version_publicada from procesos p
                where p.empresa_id = :empresa and p.activo
                  and (p.carpeta_id is null or p.carpeta_id <> all(cast(:carpetasOcultas as bigint[]))
                       or p.id = any(cast(:procesosVisibles as bigint[])))
                  and p.id <> all(cast(:procesosOcultos as bigint[]))
            ),
            aciertos as (
                select 'PROCESO' as tipo, 1 as orden, p.id as fila,
                       ts_rank_cd(p.busqueda, websearch_to_tsquery(configuracion_de_busqueda(:empresa), :texto), 1)
                           as rango
                from procesos p
                join visibles v on v.id = p.id
                where p.busqueda @@ websearch_to_tsquery(configuracion_de_busqueda(:empresa), :texto)
                  and coalesce(:tipo, 'PROCESO') = 'PROCESO'
                union all
                select 'ELEMENTO', 2, e.fila, e.rango
                from (
                    select distinct on (i.proceso_id, i.bpmn_id) i.id as fila,
                           ts_rank_cd(i.busqueda, websearch_to_tsquery(configuracion_de_busqueda(:empresa), :texto), 1)
                               as rango
                    from elementos_buscables i
                    join visibles v on v.id = i.proceso_id
                    where i.busqueda @@ websearch_to_tsquery(configuracion_de_busqueda(:empresa), :texto)
                      and (i.version_numero is null or i.version_numero = v.version_publicada)
                      and coalesce(:tipo, 'ELEMENTO') = 'ELEMENTO'
                    order by i.proceso_id, i.bpmn_id, rango desc, i.version_numero nulls first
                ) e
                union all
                select 'DOCUMENTO', 3, d.id,
                       ts_rank_cd(a.busqueda, websearch_to_tsquery(configuracion_de_busqueda(:empresa), :texto), 1)
                from versiones_documento a
                join documentos d on d.id = a.documento_id and d.version_actual = a.numero and d.activo
                join visibles v on v.id = d.proceso_id
                where a.busqueda @@ websearch_to_tsquery(configuracion_de_busqueda(:empresa), :texto)
                  and coalesce(:tipo, 'DOCUMENTO') = 'DOCUMENTO'
                union all
                select 'COMENTARIO', 4, c.id,
                       ts_rank_cd(c.busqueda, websearch_to_tsquery(configuracion_de_busqueda(:empresa), :texto), 1)
                from comentarios c
                join visibles v on v.id = c.proceso_id
                where c.busqueda @@ websearch_to_tsquery(configuracion_de_busqueda(:empresa), :texto)
                  and coalesce(:tipo, 'COMENTARIO') = 'COMENTARIO'
            )
            """;

    /** Una pagina de lo encontrado, lo mas relevante primero, con cuanto hay en total. */
    static final String PAGINA = CON_LO_VISIBLE + """
            select tipo, fila, count(*) over () as total
            from aciertos
            order by rango desc, orden, fila
            limit :tamano offset :desde
            """;

    /** Cuanto hay, para una pagina que ya no trae nada. */
    static final String CONTEO = CON_LO_VISIBLE + "select count(*) from aciertos";

    /** Lo que se muestra de cada cosa de la pagina, con el fragmento de su texto donde aparece lo buscado. */
    static final String DETALLES = """
            select 'PROCESO' as tipo, p.id as fila, p.id as proceso_id, p.nombre as proceso, p.nombre as titulo,
                   null as bpmn_id, null as tipo_de_elemento, null::integer as version, null::bigint as documento_id,
                   null::bigint as comentario_id, null::bigint as hilo_id,
                   ts_headline(configuracion_de_busqueda(:empresa),
                               translate(concat_ws(E'\\n', p.nombre, p.descripcion, p.categoria,
                                                   array_to_string(p.etiquetas, ', '), documentacion_de(p.id, null)),
                                         :marcas, ''),
                               websearch_to_tsquery(configuracion_de_busqueda(:empresa), :texto), :opciones)
                       as fragmento
            from procesos p
            where p.empresa_id = :empresa and p.id = any(cast(:procesos as bigint[]))
            union all
            select 'ELEMENTO', i.id, i.proceso_id, p.nombre, coalesce(i.nombre, i.bpmn_id), i.bpmn_id, i.tipo,
                   i.version_numero, null, null, null,
                   ts_headline(configuracion_de_busqueda(:empresa),
                               translate(concat_ws(E'\\n', i.nombre, i.detalle,
                                                   documentacion_de(i.proceso_id, i.bpmn_id)), :marcas, ''),
                               websearch_to_tsquery(configuracion_de_busqueda(:empresa), :texto), :opciones)
            from elementos_buscables i
            join procesos p on p.id = i.proceso_id
            where i.empresa_id = :empresa and i.id = any(cast(:elementos as bigint[]))
            union all
            select 'DOCUMENTO', d.id, d.proceso_id, p.nombre, d.nombre, d.bpmn_id, null, null, d.id, null, null,
                   ts_headline(configuracion_de_busqueda(:empresa),
                               translate(concat_ws(E'\\n', translate(a.nombre, '._', '  '), left(a.texto, 100000)),
                                         :marcas, ''),
                               websearch_to_tsquery(configuracion_de_busqueda(:empresa), :texto), :opciones)
            from documentos d
            join versiones_documento a on a.documento_id = d.id and a.numero = d.version_actual
            join procesos p on p.id = d.proceso_id
            where d.empresa_id = :empresa and d.id = any(cast(:documentos as bigint[]))
            union all
            select 'COMENTARIO', c.id, c.proceso_id, p.nombre, u.nombre, h.bpmn_id, null, h.version_numero, null,
                   c.id, h.id,
                   ts_headline(configuracion_de_busqueda(:empresa), translate(c.markdown, :marcas, ''),
                               websearch_to_tsquery(configuracion_de_busqueda(:empresa), :texto), :opciones)
            from comentarios c
            join comentarios h on h.id = coalesce(c.padre_id, c.id)
            join procesos p on p.id = c.proceso_id
            join usuarios u on u.id = c.autor_id
            where c.empresa_id = :empresa and c.id = any(cast(:comentarios as bigint[]))
            """;

    /** Una fila de la pagina: que es, su id en su tabla y cuantas hay en total. */
    record Acierto(String tipo, Long fila, Long total) {
    }

    /** Lo que se muestra de una fila de la pagina; el fragmento, todavia con las marcas. */
    record Fila(String tipo, Long fila, Long procesoId, String proceso, String titulo, String bpmnId,
            String tipoDeElemento, Integer version, Long documentoId, Long comentarioId, Long hiloId,
            String fragmento) {
    }

    private static final RowMapper<Acierto> ACIERTO = new DataClassRowMapper<>(Acierto.class);
    private static final RowMapper<Fila> FILA = new DataClassRowMapper<>(Fila.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final Permisos permisos;

    @Override
    public PageResponse<ResultadoDeBusquedaResponse> buscar(Actor actor, String texto, TipoDeResultado tipo,
            Pageable pagina) {
        String buscado = texto == null ? "" : texto.strip();
        if (buscado.isEmpty()) {
            return PageResponse.from(Page.empty(pagina));
        }
        Visibilidad visible = permisos.visibilidad(actor);
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("empresa", actor.empresaId())
                .addValue("texto", buscado)
                .addValue("tipo", tipo == null ? null : tipo.name(), Types.VARCHAR)
                .addValue("carpetasOcultas", arreglo(visible.carpetasOcultas()))
                .addValue("procesosVisibles", arreglo(visible.procesosVisibles()))
                .addValue("procesosOcultos", arreglo(visible.procesosOcultos()))
                .addValue("tamano", pagina.getPageSize())
                .addValue("desde", pagina.getOffset());
        List<Acierto> aciertos = jdbc.query(PAGINA, parametros, ACIERTO);
        long total = aciertos.isEmpty() ? contar(parametros, pagina) : aciertos.getFirst().total();
        return PageResponse.from(new PageImpl<>(resultados(actor.empresaId(), buscado, aciertos), pagina, total));
    }

    /** Una pagina vacia en la primera no tiene nada antes: solo despues de la primera hace falta contar. */
    private long contar(MapSqlParameterSource parametros, Pageable pagina) {
        if (pagina.getOffset() == 0) {
            return 0;
        }
        Long total = jdbc.queryForObject(CONTEO, parametros, Long.class);
        return total == null ? 0 : total;
    }

    /** Lo que se muestra de la pagina, en el orden de la pagina; lo que se borro entre las dos consultas no esta. */
    private List<ResultadoDeBusquedaResponse> resultados(Long empresaId, String texto, List<Acierto> aciertos) {
        if (aciertos.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("empresa", empresaId)
                .addValue("texto", texto)
                .addValue("marcas", MARCAS)
                .addValue("opciones", OPCIONES);
        Map<String, List<Long>> filas = aciertos.stream().collect(Collectors.groupingBy(Acierto::tipo,
                Collectors.mapping(Acierto::fila, Collectors.toList())));
        for (TipoDeResultado tipo : TipoDeResultado.values()) {
            parametros.addValue(tipo.name().toLowerCase(Locale.ROOT) + "s",
                    arreglo(filas.getOrDefault(tipo.name(), List.of())));
        }
        Map<String, ResultadoDeBusquedaResponse> encontrados = jdbc.query(DETALLES, parametros, FILA).stream()
                .collect(Collectors.toMap(fila -> clave(fila.tipo(), fila.fila()), BusquedaServiceImpl::respuesta));
        return aciertos.stream().map(acierto -> encontrados.get(clave(acierto.tipo(), acierto.fila())))
                .filter(Objects::nonNull).toList();
    }

    private static String clave(String tipo, Long fila) {
        return tipo + ":" + fila;
    }

    static ResultadoDeBusquedaResponse respuesta(Fila fila) {
        return new ResultadoDeBusquedaResponse(TipoDeResultado.valueOf(fila.tipo()), fila.procesoId(),
                fila.proceso(), fila.titulo(), fila.bpmnId(), fila.tipoDeElemento(), fila.version(),
                fila.documentoId(), fila.comentarioId(), fila.hiloId(), segmentos(fila.fragmento()));
    }

    /** El fragmento de ts_headline en tramos: lo que va entre las dos marcas, resaltado; lo demas, no. */
    static List<Segmento> segmentos(String fragmento) {
        List<Segmento> segmentos = new ArrayList<>();
        if (fragmento == null) {
            return segmentos;
        }
        StringBuilder tramo = new StringBuilder();
        boolean resaltado = false;
        for (int i = 0; i < fragmento.length(); i++) {
            char caracter = fragmento.charAt(i);
            if (caracter == INICIO || caracter == FIN) {
                agregar(segmentos, tramo, resaltado);
                resaltado = caracter == INICIO;
            } else {
                tramo.append(caracter);
            }
        }
        agregar(segmentos, tramo, resaltado);
        return segmentos;
    }

    private static void agregar(List<Segmento> segmentos, StringBuilder tramo, boolean resaltado) {
        if (!tramo.isEmpty()) {
            segmentos.add(new Segmento(tramo.toString(), resaltado));
            tramo.setLength(0);
        }
    }

    /** Un arreglo de PostgreSQL con los ids, en orden: {@code {1,2,3}}. */
    static String arreglo(Collection<Long> ids) {
        return ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }
}
