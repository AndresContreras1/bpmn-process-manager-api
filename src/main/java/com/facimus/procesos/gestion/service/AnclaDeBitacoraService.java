package com.facimus.procesos.gestion.service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * PR 61: el ancla diaria de la bitacora. Una vez al dia, el ultimo eslabon de cada tienda se copia a un almacen donde
 * nadie lo puede cambiar ni borrar. Quien reescribiera la bitacora entera, recalculando cada hash, no puede reescribir
 * el ancla: la verificacion la compara y lo descubre.
 */
public interface AnclaDeBitacoraService {

    /** Ancla cada tienda cuya bitacora crecio desde su ultima ancla; devuelve cuantas anclo. */
    int anclar(LocalDate dia);

    /** La ultima ancla de una tienda, leida del almacen y no de la base, que es lo que se pone a prueba. */
    Optional<Ancla> ultima(Long empresaId);

    /** Lo que dice un ancla. {@code hash} es {@code null} si el almacen ya no la tiene. */
    record Ancla(LocalDate dia, long secuencia, String hash) {
    }
}
