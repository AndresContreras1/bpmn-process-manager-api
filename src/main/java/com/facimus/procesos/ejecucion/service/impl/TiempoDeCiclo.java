package com.facimus.procesos.ejecucion.service.impl;

import java.util.List;

import com.facimus.procesos.ejecucion.dto.response.CicloDeCasoResponse;

/**
 * Cuanto tarda un pedido de punta a punta, a partir de los ticks que tardo cada uno de los terminados.
 *
 * <p>El promedio y el p95 se calculan aqui y no en SQL a proposito: el percentil no se escribe igual en H2 que en
 * PostgreSQL, y un numero que se publica no puede salir distinto segun el motor que haya debajo. La lista son los
 * pedidos de una tienda, no de todas, asi que cabe de sobra en memoria.
 */
final class TiempoDeCiclo {

    private TiempoDeCiclo() {
    }

    /** Los ticks vienen ya ordenados de menor a mayor: es la consulta la que los ordena. */
    static CicloDeCasoResponse de(List<Integer> ticks) {
        if (ticks.isEmpty()) {
            return CicloDeCasoResponse.sinDatos();
        }
        double medio = ticks.stream().mapToInt(Integer::intValue).average().orElseThrow();
        return new CicloDeCasoResponse(ticks.size(), Math.round(medio * 100) / 100.0, p95(ticks));
    }

    /**
     * El tiempo del pedido que hace el rango 95: el que deja por debajo o igual al menos al 95 % de ellos. Con
     * veinte pedidos es el decimonoveno, con diez el decimo y con uno el unico que hay.
     *
     * <p>No es el maximo, y esa es toda la gracia: de veinte pedidos, uno lento no mueve el p95, y dos si. Es un
     * tiempo que un pedido tardo de verdad, no un promedio disfrazado.
     */
    private static int p95(List<Integer> ticks) {
        return ticks.get((int) Math.ceil(ticks.size() * 0.95) - 1);
    }
}
