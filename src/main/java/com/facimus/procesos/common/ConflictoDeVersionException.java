package com.facimus.procesos.common;

/** 409: el cliente edito una version vieja; otra persona guardo un cambio despues de que el leyera el recurso. */
public class ConflictoDeVersionException extends RuntimeException {

    public ConflictoDeVersionException(Long versionLeida, Long versionActual) {
        super("Otra persona guardó un cambio después de que leíste este recurso: "
                + (versionLeida == null ? "no enviaste la versión que leíste" : "enviaste la versión " + versionLeida)
                + " y la actual es la " + versionActual + ". Recarga y vuelve a intentar.");
    }
}
