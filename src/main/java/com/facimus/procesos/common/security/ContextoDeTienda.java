package com.facimus.procesos.common.security;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * PR 51: de que tienda son las filas que este hilo puede ver y escribir en PostgreSQL. La base lo hace cumplir con
 * seguridad por filas; aqui solo se dice, y el {@code DataSource} de la API lo pasa a cada conexion antes de cada
 * sentencia.
 *
 * <ul>
 * <li>Una tienda: lo de una peticion con sesion, o lo que un trabajo hace por una tienda.</li>
 * <li>Sistema: lo que todavia no tiene tienda (el login por correo, el registro, los enlaces del correo, la vuelta de
 * un proveedor) y lo que cruza tiendas a proposito (la purga, la cola, el reloj). Se declara por su nombre, con
 * {@link #comoSistema}, en esos sitios y en ningun otro.</li>
 * <li>Sin decir: lo decide el {@code DataSource}. En produccion, ninguna fila; en las pruebas, sistema, para que la
 * prueba pueda preparar y mirar la base.</li>
 * </ul>
 */
public final class ContextoDeTienda {

    /** Lo que vale mientras dura: una tienda, o el sistema. */
    public record Alcance(Long empresaId, boolean sistema) {

        public static final Alcance SISTEMA = new Alcance(null, true);
        public static final Alcance NINGUNA = new Alcance(null, false);

        public static Alcance deLaTienda(Long empresaId) {
            return new Alcance(empresaId, false);
        }
    }

    private static final ThreadLocal<Alcance> ACTUAL = new ThreadLocal<>();

    private ContextoDeTienda() {
    }

    /** Lo que este hilo dijo, si dijo algo. */
    public static Optional<Alcance> actual() {
        return Optional.ofNullable(ACTUAL.get());
    }

    /** Lo hace sin tienda: todas las filas. Solo en los sitios que lo necesitan, y nombrados en su javadoc. */
    public static <T> T comoSistema(Supplier<T> trabajo) {
        return con(Alcance.SISTEMA, trabajo);
    }

    public static void comoSistema(Runnable trabajo) {
        con(Alcance.SISTEMA, () -> {
            trabajo.run();
            return null;
        });
    }

    /** Lo hace con las filas de esa tienda y ninguna otra. */
    public static <T> T deLaTienda(Long empresaId, Supplier<T> trabajo) {
        return con(Alcance.deLaTienda(empresaId), trabajo);
    }

    public static void deLaTienda(Long empresaId, Runnable trabajo) {
        con(Alcance.deLaTienda(empresaId), () -> {
            trabajo.run();
            return null;
        });
    }

    /** Deja el alcance dicho hasta {@link #restaurar}: para un filtro, que lo pone y lo quita alrededor de la peticion. */
    public static Alcance fijar(Alcance alcance) {
        Alcance anterior = ACTUAL.get();
        ACTUAL.set(alcance);
        return anterior;
    }

    public static void restaurar(Alcance anterior) {
        if (anterior == null) {
            ACTUAL.remove();
        } else {
            ACTUAL.set(anterior);
        }
    }

    private static <T> T con(Alcance alcance, Supplier<T> trabajo) {
        Alcance anterior = fijar(alcance);
        try {
            return trabajo.get();
        } finally {
            restaurar(anterior);
        }
    }
}
