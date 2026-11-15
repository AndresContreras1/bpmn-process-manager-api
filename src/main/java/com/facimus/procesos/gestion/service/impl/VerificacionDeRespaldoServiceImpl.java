package com.facimus.procesos.gestion.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import com.facimus.procesos.gestion.dto.response.VerificacionDeBitacoraResponse;
import com.facimus.procesos.gestion.service.BitacoraService;
import com.facimus.procesos.gestion.service.InstantaneaDelModelo;
import com.facimus.procesos.gestion.service.VerificacionDeRespaldoService;

/**
 * PR 67: cada tienda, tambien las que se dieron de baja y quedan como lapida de su bitacora. La cadena la verifica
 * {@link BitacoraService#verificar}, la misma del endpoint de verificacion, y la huella la rehace el modelo con
 * {@link InstantaneaDelModelo#huellaDe}, la misma que se calculo al publicar. Una version que ni siquiera se puede leer
 * tambien es un fallo, no un error que pare lo demas.
 */
@Service
public class VerificacionDeRespaldoServiceImpl implements VerificacionDeRespaldoService {

    private static final String TIENDAS = "select id from empresas order by id";

    private static final String VERSIONES = """
            select proceso_id, numero, huella, definicion from versiones_proceso
            where empresa_id = ? order by proceso_id, numero
            """;

    private final JdbcTemplate jdbc;
    private final BitacoraService bitacora;
    private final InstantaneaDelModelo modelo;

    public VerificacionDeRespaldoServiceImpl(JdbcTemplate jdbc, BitacoraService bitacora,
            InstantaneaDelModelo modelo) {
        this.jdbc = jdbc;
        this.bitacora = bitacora;
        this.modelo = modelo;
    }

    @Override
    public Informe verificar() {
        List<Long> tiendas = jdbc.queryForList(TIENDAS, Long.class);
        List<Fallo> fallos = new ArrayList<>();
        long registros = 0;
        int versiones = 0;
        for (Long tienda : tiendas) {
            VerificacionDeBitacoraResponse cadena = bitacora.verificar(tienda);
            registros += cadena.registros();
            if (!cadena.integra()) {
                fallos.add(new Fallo(tienda, "La bitácora se rompe en el registro " + cadena.rotaEn() + ": "
                        + cadena.motivo()));
            }
            versiones += revisarVersiones(tienda, fallos);
        }
        return new Informe(tiendas.size(), registros, versiones, List.copyOf(fallos));
    }

    /** Las versiones de una tienda, de a una fila: la definicion puede ser grande y no hace falta tenerlas todas. */
    private int revisarVersiones(Long tienda, List<Fallo> fallos) {
        int[] revisadas = {0};
        jdbc.query(VERSIONES, (RowCallbackHandler) fila -> {
            revisadas[0]++;
            String version = "La versión " + fila.getInt("numero") + " del proceso " + fila.getLong("proceso_id");
            try {
                if (!modelo.huellaDe(fila.getString("definicion")).equals(fila.getString("huella"))) {
                    fallos.add(new Fallo(tienda, version + " ya no tiene la huella con que se publicó."));
                }
            } catch (RuntimeException e) {
                fallos.add(new Fallo(tienda, version + " no se puede leer: " + e.getMessage()));
            }
        }, tienda);
        return revisadas[0];
    }
}
