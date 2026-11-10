package com.facimus.procesos.modelado.model;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * PR 52: el id BPMN de un elemento nuevo: su tipo y doce cifras al azar, como Activity_3f9a0c1b2d4e. Es un NCName, como
 * pide BPMN, no cambia nunca y no depende del id de la base, que el elemento todavia no tiene al guardarse. Los que ya
 * existian lo recibieron en la migracion V41, con su id: Activity_42.
 */
public final class BpmnId {

    private static final SecureRandom AZAR = new SecureRandom();

    private BpmnId() {
    }

    public static String nuevo(String tipo) {
        byte[] bytes = new byte[6];
        AZAR.nextBytes(bytes);
        return tipo + "_" + HexFormat.of().formatHex(bytes);
    }
}
