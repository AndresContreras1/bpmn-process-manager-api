package com.facimus.procesos.security;

import java.time.Duration;
import java.util.Optional;

/**
 * Cuenta intentos por clave en una ventana deslizante. Cuando una clave junta el maximo dentro de la ventana, espera a
 * que el intento mas viejo salga de ella.
 */
public interface LimiteDeIntentos {

    /** Cuanto le falta a la clave para volver a intentar, si ya gasto sus intentos dentro de la ventana. */
    Optional<Duration> espera(String clave);

    void registrar(String clave);

    /** Olvida los intentos de la clave, por ejemplo cuando por fin entro. */
    void reiniciar(String clave);
}
