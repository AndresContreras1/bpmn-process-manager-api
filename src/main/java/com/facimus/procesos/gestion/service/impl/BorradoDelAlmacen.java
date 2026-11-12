package com.facimus.procesos.gestion.service.impl;

import org.springframework.stereotype.Component;

import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.almacen.ClaveDeAlmacen;
import com.facimus.procesos.common.trabajos.ManejadorDeTrabajo;
import com.facimus.procesos.common.trabajos.Trabajo;

import lombok.RequiredArgsConstructor;

/**
 * PR 61: borra del almacen lo que guardaba una tienda borrada: sus documentos, con todas sus versiones. El ancla de la
 * bitacora no: va con la bitacora, que sobrevive a su tienda, y el almacen no la dejaria borrar. Borrar dos veces el
 * mismo prefijo no hace nada, asi que la cola lo puede reintentar sin cuidado.
 */
@Component
@RequiredArgsConstructor
public class BorradoDelAlmacen implements ManejadorDeTrabajo {

    private final Almacen almacen;

    @Override
    public String tipo() {
        return BorradoDeTiendas.BORRAR_ARCHIVOS;
    }

    @Override
    public void ejecutar(Trabajo trabajo) {
        almacen.borrarPrefijo(ClaveDeAlmacen.deEmpresa(Long.parseLong(trabajo.datos())));
    }
}
