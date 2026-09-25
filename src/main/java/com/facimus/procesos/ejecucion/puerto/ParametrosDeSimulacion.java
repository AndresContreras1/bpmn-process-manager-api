package com.facimus.procesos.ejecucion.puerto;

/**
 * D7: lo que la tienda decidio sobre sus socios, tal como un socio lo necesita. Es una copia de lo que guarda su
 * configuracion, no la fila: un socio no toca la base, y menos la de una tienda que no es la del mensaje que
 * esta atendiendo.
 *
 * @param semilla                 de aqui salen todas sus decisiones
 * @param tasaRechazoPagos        de cada cien pagos, cuantos rechaza la pasarela cuando la regla no lo decide
 * @param ticksRespuestaPagos     cuanto tarda la pasarela en contestar
 * @param reglaRechazoPagos       cuando rechazar seguro, en la gramatica del proyecto; vacia deja mandar a la tasa
 * @param ticksRespuestaTransporte cuanto tarda el transportista en contestar
 * @param ticksEntrega            cuanto tarda el paquete en llegar desde que lo recoge
 * @param tasaPerdidaEnvios       de cada cien envios, cuantos se pierden
 * @param tasaFalloNotificaciones de cada cien notificaciones, cuantas no llegan
 */
public record ParametrosDeSimulacion(long semilla, int tasaRechazoPagos, int ticksRespuestaPagos,
        String reglaRechazoPagos, int ticksRespuestaTransporte, int ticksEntrega, int tasaPerdidaEnvios,
        int tasaFalloNotificaciones) {

    /** Lo de fabrica, que es lo que usa quien no dice nada: util para armar un socio en una prueba. */
    public static ParametrosDeSimulacion deFabrica() {
        return new ParametrosDeSimulacion(42L, 10, 1, null, 1, 3, 5, 2);
    }
}
