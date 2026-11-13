package com.facimus.procesos.common.formularios;

/**
 * PR 69: lo que un campo de un formulario pide, y con eso que validaciones admite. Una seccion no pide nada: titula y
 * agrupa los campos que vienen despues de ella, hasta la siguiente seccion.
 */
public enum TipoDeCampo {

    /** Una linea de texto. */
    TEXTO(1_000),
    /** Varias lineas de texto. */
    TEXTO_LARGO(10_000),
    NUMERO(0),
    /** Un importe, con los decimales de su moneda. */
    MONEDA(0),
    /** Un dia, sin hora: {@code 2026-11-12}. */
    FECHA(0),
    /**
     * Un dia y una hora de reloj, la local de la organizacion y sin zona: {@code 2026-11-12T09:30:00}. Se guarda como se
     * escribio; una hora que un cambio de horario salta o repite no se revisa.
     */
    FECHA_HORA(0),
    SI_NO(0),
    /** Una de las opciones. */
    LISTA(0),
    /** Varias de las opciones. */
    LISTA_MULTIPLE(0),
    /** Una persona de la organizacion, por su id. */
    PERSONA(0),
    /** Un documento del proceso, por su id. */
    ARCHIVO(0),
    SECCION(0);

    private final int largoTope;

    TipoDeCampo(int largoTope) {
        this.largoTope = largoTope;
    }

    /** Cuantos caracteres admite un texto de este tipo, aunque el campo no diga nada; cero si no es texto. */
    public int largoTope() {
        return largoTope;
    }

    /** Si quien llena el formulario le da un valor. La seccion es la unica que no. */
    public boolean llevaValor() {
        return this != SECCION;
    }

    public boolean esTexto() {
        return largoTope > 0;
    }

    /** Si su valor se elige de una lista de opciones. */
    public boolean conOpciones() {
        return this == LISTA || this == LISTA_MULTIPLE;
    }

    /** Si admite un minimo y un maximo numericos: el valor, o cuantas opciones se eligen. */
    public boolean conMinimoYMaximo() {
        return this == NUMERO || this == MONEDA || this == LISTA_MULTIPLE;
    }

    /** Si admite una fecha desde y una hasta. */
    public boolean conDesdeYHasta() {
        return this == FECHA || this == FECHA_HORA;
    }
}
