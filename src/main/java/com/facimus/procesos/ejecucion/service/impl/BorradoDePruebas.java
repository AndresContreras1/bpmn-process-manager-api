package com.facimus.procesos.ejecucion.service.impl;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * PR 68: lo que dejaron los casos de una version de prueba, de las hojas a la raiz, como el borrado de una tienda. La
 * version la borra gestion, que es de quien es; aqui se borra lo que colgo de ella en la ejecucion.
 *
 * <p>Cada borrado vuelve a pedir que la version sea de prueba, por su cuenta: si alguna vez llegara aqui el id de una
 * publicada, sus casos, su historia y sus mensajes se quedan donde estan. Corre en la transaccion de quien lo llama.
 */
@Component
@RequiredArgsConstructor
public class BorradoDePruebas {

    /** Los casos de esa version, si es de prueba. */
    private static final String SUS_CASOS = """
            select c.id from casos c join versiones_proceso v on v.id = c.version_proceso_id
            where v.id = ? and v.tipo = 'PRUEBA'""";

    private static final List<String> DE_LAS_HOJAS_A_LA_RAIZ = List.of(
            "delete from temporizadores where version_proceso_id in "
                    + "(select v.id from versiones_proceso v where v.id = ? and v.tipo = 'PRUEBA')",
            "delete from delegaciones where caso_id in (" + SUS_CASOS + ")",
            "delete from eventos_caso where caso_id in (" + SUS_CASOS + ")",
            "delete from actividades_caso where caso_id in (" + SUS_CASOS + ")",
            "delete from mensajes_salientes where caso_id in (" + SUS_CASOS + ")",
            "delete from mensajes_entrantes where caso_id in (" + SUS_CASOS + ")",
            "delete from casos where id in (" + SUS_CASOS + ")");

    private final JdbcTemplate jdbc;

    /** Borra lo que los casos de esa version de prueba dejaron, y dice cuantos casos eran. */
    public int borrar(Long versionId) {
        int casos = 0;
        for (String borrado : DE_LAS_HOJAS_A_LA_RAIZ) {
            casos = jdbc.update(borrado, versionId);
        }
        return casos;
    }
}
