package com.facimus.procesos.integracion.simulado;

/**
 * D7: lo que deciden los socios simulados sale de un hash, no de {@code Random}. La misma tienda, la misma semilla
 * y los mismos pasos dan siempre el mismo resultado, que es lo que separa una simulacion que se puede mostrar dos
 * veces de una prueba que a veces pasa.
 *
 * <p>No es aleatoriedad de verdad y no pretende serlo: lo unico que hace falta es que dos pedidos distintos no
 * corran la misma suerte y que el mismo pedido corra siempre la suya.
 */
final class SemillaDeterminista {

    private SemillaDeterminista() {
    }

    /**
     * Si a este mensaje de este caso le toca, con una tasa de 0 a 100.
     *
     * <p>Los dos extremos salen solos y no necesitan un caso aparte: con tasa cero ningun resto es menor que cero,
     * y con cien todos lo son. Son las dos formas de forzar el resultado desde una prueba o desde una demo, y
     * escribirlas a mano seria escribir dos veces lo mismo.
     */
    static boolean leToca(long semilla, Long casoId, String mensaje, int tasa) {
        return Math.floorMod(mezclar(semilla, casoId, mensaje), 100) < tasa;
    }

    /**
     * Un numero de 0 a {@code tope - 1}, estable para los mismos tres. Es lo que usa quien no decide si o no sino
     * cuanto: el monto de un pedido inventado, por ejemplo.
     */
    static int numero(long semilla, Long casoId, String mensaje, int tope) {
        return Math.floorMod(mezclar(semilla, casoId, mensaje), tope);
    }

    /** Un numero estable a partir de los tres: sin estado, sin reloj y sin nada que cambie entre dos corridas. */
    private static int mezclar(long semilla, Long casoId, String mensaje) {
        long mezcla = semilla * 31 + (casoId == null ? 0 : casoId);
        mezcla = mezcla * 31 + (mensaje == null ? 0 : mensaje.hashCode());
        mezcla ^= mezcla >>> 27;
        return (int) (mezcla * 0x2545F4914F6CDD1DL >>> 32);
    }
}
