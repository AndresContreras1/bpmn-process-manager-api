package com.facimus.procesos.modelado.service.impl;

/**
 * R-33: una caja negra es un participante del que solo se ve lo que entra y lo que sale. La regla se comprueba en
 * dos momentos, al crear una lane dentro de un pool y al marcar como caja negra un pool que ya tiene lanes, asi
 * que el mensaje vive en un solo sitio.
 */
final class ReglasDePools {

    static final String CAJA_NEGRA_SIN_LANES = "Un pool de caja negra no puede tener lanes.";

    private ReglasDePools() {
    }
}
