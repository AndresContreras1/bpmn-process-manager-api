package com.facimus.procesos.modelado.service.impl;

import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.facimus.procesos.gestion.service.ProcesoDeElemento;

import lombok.RequiredArgsConstructor;

/** PR 60: cada clase de elemento llega a su proceso por un camino distinto. */
@Component
@RequiredArgsConstructor
public class ProcesoDeElementoEnModelo implements ProcesoDeElemento {

    private static final String DE_NODO = """
            select p.proceso_id from nodos_flujo n join lanes l on l.id = n.lane_id join pools p on p.id = l.pool_id
            where n.id = ? and n.empresa_id = ?
            """;

    private static final Map<String, String> CONSULTAS = Map.of(
            "pools", "select proceso_id from pools where id = ? and empresa_id = ?",
            "lanes", "select p.proceso_id from lanes l join pools p on p.id = l.pool_id "
                    + "where l.id = ? and l.empresa_id = ?",
            "actividades", DE_NODO, "gateways", DE_NODO, "eventos", DE_NODO, "nodos", DE_NODO,
            "arcos", "select p.proceso_id from arcos a join pools p on p.id = a.pool_id "
                    + "where a.id = ? and a.empresa_id = ?",
            "mensajes", "select p.proceso_id from mensajes m join pools p on p.id = m.pool_origen_id "
                    + "where m.id = ? and m.empresa_id = ?",
            "artefactos", "select proceso_id from artefactos where id = ? and empresa_id = ?",
            "asociaciones", "select proceso_id from asociaciones where id = ? and empresa_id = ?");

    private final JdbcTemplate jdbc;

    @Override
    public Optional<Long> procesoDe(Long empresaId, String tipo, Long id) {
        String consulta = CONSULTAS.get(tipo);
        if (consulta == null) {
            return Optional.empty();
        }
        return jdbc.queryForList(consulta, Long.class, id, empresaId).stream().findFirst();
    }
}
