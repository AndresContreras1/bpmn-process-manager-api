package com.facimus.procesos.e2e;

import org.openqa.selenium.By;

/**
 * Los localizadores de cada pantalla, en un sitio.
 *
 * Todos son por `id` o por `data-testid`, nunca por clase de Bootstrap ni por la forma del HTML: el frontend pone
 * un `id` en lo unico y un `data-testid` en lo repetido justo para esto, y asi cambiar el aspecto de una pantalla
 * no rompe una prueba que no tiene nada que ver con el aspecto.
 */
final class Paginas {

    private Paginas() {
    }

    /** El menu de la cuenta, donde vive la administracion. */
    static final class Navbar {
        static final By USUARIOS = By.id("navbar-usuarios");
        static final By HISTORIAL = By.id("navbar-historial");
    }

    /** Entrar. */
    static final class Login {
        static final By EMAIL = By.id("login-email");
        static final By CLAVE = By.id("login-password");
        static final By ENTRAR = By.id("login-enviar");
        static final By ERROR = By.id("login-error");
    }

    /** La lista de procesos. */
    static final class Procesos {
        static final By NUEVO = By.id("procesos-nuevo");
        static final By FILA = By.cssSelector("[data-testid='proceso-nombre']");
        static final By AVISO = By.id("procesos-aviso");
        static final By RECIBIDO = By.cssSelector("[data-testid='proceso-recibido']");
    }

    /** Crear o editar un proceso. */
    static final class Formulario {
        static final By NOMBRE = By.id("proceso-form-nombre");
        static final By CATEGORIA = By.id("proceso-form-categoria");
        static final By DESCRIPCION = By.id("proceso-form-descripcion");
        static final By GUARDAR = By.id("proceso-form-guardar");
        static final By CONFLICTO = By.id("proceso-form-conflicto");
    }

    /** El detalle de un proceso. */
    static final class Detalle {
        static final By NOMBRE = By.id("proceso-detalle-nombre");
        static final By ESTADO = By.id("proceso-detalle-estado");
        static final By MODELAR = By.id("proceso-detalle-modelar");
        static final By COMPARTIR = By.id("proceso-detalle-compartir");
        static final By EDITAR = By.id("proceso-detalle-editar");
        static final By CONTEO = By.id("proceso-detalle-conteo");
        static final By VERSION = By.id("proceso-detalle-version");
        static final By CONGELADO = By.id("proceso-detalle-congelado");
        static final By RETIRAR = By.id("proceso-detalle-retirar");
        static final By CONFIRMAR_RETIRO = By.id("modal-retirar-confirmar");
        static final By INVITADA = By.id("proceso-detalle-invitada");
        static final By AVISO = By.id("proceso-detalle-aviso");
    }

    /** El editor del diagrama. */
    static final class Editor {
        static final By TITULO = By.id("proceso-editor-titulo");
        static final By CUENTA = By.id("proceso-editor-cuenta");
        static final By LIMPIO = By.id("proceso-editor-limpio");
        static final By HALLAZGO = By.cssSelector("[data-testid='hallazgo']");
        static final By PUBLICAR = By.id("proceso-editor-publicar");
        static final By PUBLICADO = By.id("proceso-editor-publicado");
        static final By NODO = By.cssSelector("[data-testid='esquema-nodo']");
        static final By LANE = By.cssSelector("[data-testid='esquema-lane']");
        static final By ELEGIDO = By.id("proceso-editor-elegido");
        static final By GUARDAR = By.id("panel-elemento-guardar");
        static final By NOMBRE_ACTIVIDAD = By.id("panel-actividad-nombre");
    }

    /** Usuarios. */
    static final class Usuarios {
        static final By NOMBRE = By.id("usuarios-nombre");
        static final By EMAIL = By.id("usuarios-email");
        static final By CREAR = By.id("usuarios-crear");
        static final By CLAVE_TEMPORAL = By.id("usuarios-clave-temporal");
        static final By FILA = By.cssSelector("[data-testid='usuario']");
        static final By SOLO_ADMIN = By.id("usuarios-solo-admin");
        static final By AVISO = By.id("usuarios-aviso");
        static final By BUSCAR = By.id("usuarios-buscar");
        static final By INACTIVOS = By.id("usuarios-inactivos");
        static final By VACIO = By.id("usuarios-vacio");
        static final By BAJA = By.cssSelector("[data-testid='usuario-baja']");
        static final By ALTA = By.cssSelector("[data-testid='usuario-alta']");
        static final By CONFIRMAR_BAJA = By.id("modal-baja-usuario-confirmar");
        static final By ROLES = By.cssSelector("[data-testid='usuario-roles']");
        static final By CAJA_DE_ROL = By.cssSelector("[data-testid='usuario-panel-roles'] input[type='checkbox']");
        static final By GUARDAR_ROLES = By.id("usuarios-guardar-roles");
    }

    /** Roles de proceso. */
    static final class Roles {
        static final By NOMBRE = By.id("roles-nombre");
        static final By DESCRIPCION = By.id("roles-descripcion");
        static final By GUARDAR = By.id("roles-guardar");
        static final By FILA = By.cssSelector("[data-testid='rol']");
        static final By AVISO = By.id("roles-aviso");
    }

    /** La lista de casos y el panel de abrir uno. */
    static final class Casos {
        static final By ABRIR_PANEL = By.id("casos-abrir-panel");
        static final By PROCESO = By.id("casos-abrir-proceso");
        static final By REFERENCIA = By.id("casos-abrir-referencia");
        static final By ENVIAR = By.id("casos-abrir-enviar");
        static final By FILA = By.cssSelector("[data-testid='caso-referencia']");
        static final By VACIO = By.id("casos-vacio");
    }

    /** Un caso por dentro. */
    static final class Caso {
        static final By REFERENCIA = By.id("caso-detalle-referencia");
        static final By ESTADO = By.id("caso-detalle-estado");
        static final By EVENTO = By.cssSelector("[data-testid='caso-evento']");
        static final By PASO = By.cssSelector("[data-testid='caso-paso']");
        static final By ACTIVO = By.cssSelector("#diagrama-bpmn .nodo.activo");
        static final By RECORRIDO = By.cssSelector("#diagrama-bpmn .nodo.recorrido");
        static final By VARIABLE = By.cssSelector("[data-testid='caso-variable-valor']");
    }

    /** La bandeja de tareas. */
    static final class Tareas {
        static final By MIAS = By.id("tareas-mias");
        static final By FILA = By.cssSelector("[data-testid='tarea-fila']");
        static final By NODO = By.cssSelector("[data-testid='tarea-nodo']");
        static final By TOMAR = By.cssSelector("[data-testid='tarea-tomar']");
        static final By COMPLETAR = By.cssSelector("[data-testid='tarea-completar']");
        static final By DATO_NUEVO = By.cssSelector("[data-testid='tarea-dato-nuevo']");
        static final By DATO_CLAVE = By.cssSelector("[data-testid='tarea-dato-clave']");
        static final By DATO_VALOR = By.cssSelector("[data-testid='tarea-dato-valor']");
        static final By CONFIRMAR = By.id("tareas-confirmar");
        static final By AVISO = By.id("tareas-aviso");
        static final By VACIO = By.id("tareas-vacio");
    }

    /** El panel de simulacion. */
    static final class Simulacion {
        static final By RELOJ = By.id("simulacion-reloj");
        static final By TICKS = By.id("simulacion-ticks");
        static final By AVANZAR = By.id("simulacion-avanzar");
        static final By AVISO = By.id("simulacion-aviso");
        static final By ENTREGA = By.id("simulacion-ticks-entrega");
        static final By GUARDAR_SOCIOS = By.id("simulacion-guardar-socios");
        static final By SOLO_ADMIN = By.id("simulacion-solo-admin");
    }

    /** El tablero de la operacion. */
    static final class Tablero {
        static final By PROCESO = By.id("tablero-proceso");
        static final By CASOS = By.id("tablero-casos");
        static final By TERMINADOS = By.id("tablero-terminados");
        static final By TODO_BIEN = By.id("tablero-todo-bien");
    }

    /** Compartir un proceso. */
    static final class Compartir {
        static final By NIT = By.id("compartir-nit");
        static final By ENVIAR = By.id("compartir-enviar");
        static final By INVITADA = By.cssSelector("[data-testid='invitada']");
        static final By AVISO = By.id("compartir-aviso");
    }
}
