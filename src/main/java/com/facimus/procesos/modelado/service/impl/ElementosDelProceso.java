package com.facimus.procesos.modelado.service.impl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * PR 61: si un {@code bpmnId} es de un elemento vivo de un proceso. Cada clase de elemento vive en su tabla y llega
 * a su proceso por otro camino; una sola consulta las recorre todas y para en la primera que lo tiene.
 */
@Component
@RequiredArgsConstructor
public class ElementosDelProceso {

    private static final String EXISTE = """
            select exists (
                select 1 from pools where proceso_id = ? and bpmn_id = ? and empresa_id = ? and activo
                union all
                select 1 from lanes l join pools p on p.id = l.pool_id
                where p.proceso_id = ? and l.bpmn_id = ? and l.empresa_id = ? and l.activo
                union all
                select 1 from nodos_flujo n join lanes l on l.id = n.lane_id join pools p on p.id = l.pool_id
                where p.proceso_id = ? and n.bpmn_id = ? and n.empresa_id = ? and n.activo
                union all
                select 1 from arcos a join pools p on p.id = a.pool_id
                where p.proceso_id = ? and a.bpmn_id = ? and a.empresa_id = ? and a.activo
                union all
                select 1 from mensajes where proceso_id = ? and bpmn_id = ? and empresa_id = ? and activo
                union all
                select 1 from artefactos where proceso_id = ? and bpmn_id = ? and empresa_id = ? and activo)
            """;

    private final JdbcTemplate jdbc;

    public boolean existe(Long empresaId, Long procesoId, String bpmnId) {
        Object[] porTabla = {procesoId, bpmnId, empresaId};
        Object[] parametros = new Object[porTabla.length * 6];
        for (int tabla = 0; tabla < 6; tabla++) {
            System.arraycopy(porTabla, 0, parametros, tabla * porTabla.length, porTabla.length);
        }
        return Boolean.TRUE.equals(jdbc.queryForObject(EXISTE, Boolean.class, parametros));
    }
}
