package com.facimus.procesos.gestion.dto.response;

/** Lo que encuentra un POST con Idempotency-Key al reservar su clave. */
public sealed interface ReservaIdempotencia {

    /** La clave es nueva, o su peticion anterior quedo abandonada: la peticion se ejecuta. */
    record Nueva(Long id) implements ReservaIdempotencia {
    }

    /** La peticion ya se hizo con esta clave: se devuelve lo que respondio entonces. */
    record Repetida(int estado, String cuerpo, String ubicacion) implements ReservaIdempotencia {
    }

    /** Otra peticion con esta clave sigue en curso. */
    record EnCurso() implements ReservaIdempotencia {
    }

    /** La clave ya se uso con otra peticion (otra ruta u otro cuerpo). */
    record Distinta() implements ReservaIdempotencia {
    }
}
