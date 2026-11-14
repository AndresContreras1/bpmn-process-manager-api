package com.facimus.procesos.common.formularios;

import static com.facimus.procesos.common.formularios.CampoArmado.campo;
import static com.facimus.procesos.common.formularios.CampoArmado.formulario;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** PR 69: lo que hace bien armado a un formulario, regla por regla y en sus bordes. */
class EsquemaDelFormularioTest {

    private static Map<String, String> problemas(CampoArmado... campos) {
        return EsquemaDelFormulario.problemas(formulario(campos));
    }

    @Test
    @DisplayName("Un formulario con un campo de cada tipo, bien armado, no tiene problemas")
    void unoDeCadaTipo() {
        assertThat(problemas(
                campo("datos", TipoDeCampo.SECCION).visibleSi("order.total > 0"),
                campo("nombre", TipoDeCampo.TEXTO).requerido().largo(3, 120).patron("[A-Za-z ]+"),
                campo("notas", TipoDeCampo.TEXTO_LARGO).largo(0, 10_000),
                campo("cantidad", TipoDeCampo.NUMERO).minimo("1").maximo("99"),
                campo("monto", TipoDeCampo.MONEDA).moneda("COP").minimo("0").porDefecto("order.total"),
                campo("dia", TipoDeCampo.FECHA).entre("2026-01-01", "2026-12-31"),
                campo("cita", TipoDeCampo.FECHA_HORA).entre("2026-01-01T08:00:00", "2026-01-01T17:00:00"),
                campo("urgente", TipoDeCampo.SI_NO),
                campo("metodo", TipoDeCampo.LISTA).opciones("TARJETA", "EFECTIVO"),
                campo("extras", TipoDeCampo.LISTA_MULTIPLE).opciones("A", "B", "C").minimo("1").maximo("3"),
                campo("responsable", TipoDeCampo.PERSONA).requeridoSi("urgente == true"),
                campo("soporte", TipoDeCampo.ARCHIVO).visibleSi("metodo == 'TARJETA' and cantidad > 1")))
                .isEmpty();
    }

    @Nested
    @DisplayName("El formulario entero")
    class Entero {

        @Test
        @DisplayName("Admite hasta cien campos; con ciento uno, el problema es de la lista")
        void tope() {
            List<CampoDeFormulario> cien = new ArrayList<>();
            for (int i = 0; i < EsquemaDelFormulario.TOPE_DE_CAMPOS; i++) {
                cien.add(campo("c" + i, TipoDeCampo.TEXTO).armar());
            }
            assertThat(EsquemaDelFormulario.problemas(new Formulario(cien))).isEmpty();

            cien.add(campo("otro", TipoDeCampo.TEXTO).armar());
            assertThat(EsquemaDelFormulario.problemas(new Formulario(cien)))
                    .containsExactly(Map.entry("campos", "Un formulario admite hasta 100 campos."));
        }

        @Test
        @DisplayName("Sin campos no hay problemas: un formulario vacio es ninguno")
        void vacio() {
            assertThat(EsquemaDelFormulario.problemas(new Formulario(null))).isEmpty();
        }
    }

    @Nested
    @DisplayName("El nombre")
    class Nombre {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"monto", "M", "monto_2", "montoTotal"})
        @DisplayName("Empieza por una letra y sigue con letras, digitos o _")
        void bienEscrito(String nombre) {
            assertThat(problemas(campo(nombre, TipoDeCampo.TEXTO))).isEmpty();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"2monto", "_monto", "monto total", "monto.total", "monto-total", "", "año"})
        @DisplayName("Lo demas no es el nombre de una variable")
        void malEscrito(String nombre) {
            assertThat(problemas(campo(nombre, TipoDeCampo.TEXTO))).containsOnlyKeys("campos[0].nombre");
        }

        @Test
        @DisplayName("Hasta sesenta caracteres")
        void largo() {
            assertThat(problemas(campo("a".repeat(60), TipoDeCampo.TEXTO))).isEmpty();
            assertThat(problemas(campo("a".repeat(61), TipoDeCampo.TEXTO))).containsOnlyKeys("campos[0].nombre");
        }

        @Test
        @DisplayName("Sin nombre no hay variable que llenar")
        void nulo() {
            assertThat(problemas(campo(null, TipoDeCampo.TEXTO))).containsOnlyKeys("campos[0].nombre");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"and", "OR", "not", "true", "False", "caso", "tarea"})
        @DisplayName("Las palabras de la gramatica y los prefijos del motor estan reservados")
        void reservado(String nombre) {
            assertThat(problemas(campo(nombre, TipoDeCampo.TEXTO)))
                    .containsExactly(Map.entry("campos[0].nombre", "\"" + nombre + "\" es una palabra reservada."));
        }

        @Test
        @DisplayName("Dos campos no se llaman igual, ni cambiando mayusculas")
        void repetido() {
            assertThat(problemas(campo("monto", TipoDeCampo.NUMERO), campo("Monto", TipoDeCampo.TEXTO)))
                    .containsExactly(Map.entry("campos[1].nombre", "Ya hay otro campo que se llama \"Monto\"."));
        }
    }

    @Nested
    @DisplayName("Etiqueta, ayuda y tipo")
    class EtiquetaAyudaYTipo {

        @Test
        @DisplayName("La etiqueta es obligatoria y llega hasta 200 caracteres")
        void etiqueta() {
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).etiqueta(" ")))
                    .containsExactly(Map.entry("campos[0].etiqueta", "Es obligatoria."));
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).etiqueta(null))).containsOnlyKeys("campos[0].etiqueta");
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).etiqueta("e".repeat(200)))).isEmpty();
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).etiqueta("e".repeat(201))))
                    .containsExactly(Map.entry("campos[0].etiqueta", "No puede superar 200 caracteres."));
        }

        @Test
        @DisplayName("La ayuda es opcional y llega hasta 500 caracteres")
        void ayuda() {
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).ayuda("x".repeat(500)))).isEmpty();
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).ayuda("x".repeat(501))))
                    .containsOnlyKeys("campos[0].ayuda");
        }

        @Test
        @DisplayName("Sin tipo no se revisa nada mas del campo")
        void sinTipo() {
            assertThat(problemas(campo("a", null).opciones("X").minimo("9").maximo("1")))
                    .containsExactly(Map.entry("campos[0].tipo", "El tipo es obligatorio."));
        }
    }

    @Nested
    @DisplayName("Lo que un tipo no admite")
    class LoQueNoAplica {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TipoDeCampo.class, names = {"TEXTO", "NUMERO", "FECHA", "SI_NO", "PERSONA", "SECCION"})
        @DisplayName("Opciones fuera de una lista")
        void opciones(TipoDeCampo tipo) {
            assertThat(problemas(campo("a", tipo).opciones("X")))
                    .containsExactly(Map.entry("campos[0].opciones", "No aplica a un campo de tipo " + tipo + "."));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TipoDeCampo.class, names = {"TEXTO", "FECHA", "LISTA", "PERSONA", "ARCHIVO"})
        @DisplayName("Minimo y maximo fuera de numero, moneda y lista multiple")
        void minimoYMaximo(TipoDeCampo tipo) {
            CampoArmado campo = campo("a", tipo).minimo("1").maximo("2");
            if (tipo.conOpciones()) {
                campo.opciones("X");
            }
            assertThat(problemas(campo)).containsOnlyKeys("campos[0].minimo", "campos[0].maximo");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TipoDeCampo.class, names = {"NUMERO", "MONEDA", "FECHA", "SI_NO", "ARCHIVO"})
        @DisplayName("Largos y patron fuera de los textos")
        void largosYPatron(TipoDeCampo tipo) {
            assertThat(problemas(campo("a", tipo).largo(1, 2).patron("x")))
                    .containsOnlyKeys("campos[0].largoMinimo", "campos[0].largoMaximo", "campos[0].patron");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TipoDeCampo.class, names = {"TEXTO", "NUMERO", "SI_NO"})
        @DisplayName("Desde y hasta fuera de las fechas")
        void desdeYHasta(TipoDeCampo tipo) {
            assertThat(problemas(campo("a", tipo).entre("2026-01-01", "2026-12-31")))
                    .containsOnlyKeys("campos[0].desde", "campos[0].hasta");
        }

        @Test
        @DisplayName("Moneda fuera de un campo de moneda")
        void moneda() {
            assertThat(problemas(campo("a", TipoDeCampo.NUMERO).moneda("COP")))
                    .containsExactly(Map.entry("campos[0].moneda", "No aplica a un campo de tipo NUMERO."));
        }

        @Test
        @DisplayName("Una seccion no se llena: ni obligatoria, ni con condicion de obligatoria, ni con valor por defecto")
        void seccion() {
            assertThat(problemas(campo("s", TipoDeCampo.SECCION).requerido().requeridoSi("x == 1").porDefecto("x")))
                    .containsOnlyKeys("campos[0].requerido", "campos[0].requeridoSi", "campos[0].porDefecto");
            assertThat(problemas(campo("s", TipoDeCampo.SECCION).visibleSi("x == 1"))).isEmpty();
        }

        @Test
        @DisplayName("Una lista sin opciones vacia no cuenta como opciones de mas")
        void opcionesVacias() {
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).opcionesTal(List.of()))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Las listas")
    class Listas {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TipoDeCampo.class, names = {"LISTA", "LISTA_MULTIPLE"})
        @DisplayName("Necesitan al menos una opcion")
        void sinOpciones(TipoDeCampo tipo) {
            assertThat(problemas(campo("a", tipo)))
                    .containsExactly(Map.entry("campos[0].opciones", "Una lista necesita al menos una opcion."));
        }

        @Test
        @DisplayName("Admiten hasta doscientas opciones")
        void tope() {
            String[] doscientas = new String[EsquemaDelFormulario.TOPE_DE_OPCIONES];
            for (int i = 0; i < doscientas.length; i++) {
                doscientas[i] = "O" + i;
            }
            assertThat(problemas(campo("a", TipoDeCampo.LISTA).opciones(doscientas))).isEmpty();
            String[] mas = Arrays.copyOf(doscientas, doscientas.length + 1);
            mas[doscientas.length] = "otra";
            assertThat(problemas(campo("a", TipoDeCampo.LISTA).opciones(mas)))
                    .containsExactly(Map.entry("campos[0].opciones", "Una lista admite hasta 200 opciones."));
        }

        @Test
        @DisplayName("Cada opcion tiene un valor, de hasta cien caracteres, que no se repite")
        void cadaOpcion() {
            assertThat(problemas(campo("a", TipoDeCampo.LISTA).opcionesTal(List.of(new OpcionDeLista("A", "a"),
                    new OpcionDeLista(" ", "b"), new OpcionDeLista("A", "c"), new OpcionDeLista(null, "d"),
                    new OpcionDeLista("x".repeat(101), "e"), new OpcionDeLista("x".repeat(100), "f"),
                    new OpcionDeLista("G", "g".repeat(201)), new OpcionDeLista("H", "h".repeat(200))))))
                    .containsOnly(Map.entry("campos[0].opciones[1].valor", "El valor de la opcion es obligatorio."),
                            Map.entry("campos[0].opciones[2].valor", "La opcion \"A\" esta repetida."),
                            Map.entry("campos[0].opciones[3].valor", "El valor de la opcion es obligatorio."),
                            Map.entry("campos[0].opciones[4].valor", "No puede superar 100 caracteres."),
                            Map.entry("campos[0].opciones[6].etiqueta", "No puede superar 200 caracteres."));
        }

        @Test
        @DisplayName("En una multiple, minimo y maximo cuentan opciones: enteros, de cero a cuantas hay")
        void cuantasEnUnaMultiple() {
            CampoArmado tres = campo("a", TipoDeCampo.LISTA_MULTIPLE).opciones("A", "B", "C");
            assertThat(problemas(tres.minimo("0").maximo("3"))).isEmpty();
            assertThat(problemas(campo("a", TipoDeCampo.LISTA_MULTIPLE).opciones("A", "B", "C").minimo("3")))
                    .isEmpty();
            assertThat(problemas(campo("a", TipoDeCampo.LISTA_MULTIPLE).opciones("A", "B", "C").maximo("4")))
                    .containsExactly(Map.entry("campos[0].maximo",
                            "La lista tiene 3 opciones: no se pueden elegir mas."));
            assertThat(problemas(campo("a", TipoDeCampo.LISTA_MULTIPLE).opciones("A", "B", "C").minimo("4")))
                    .containsOnlyKeys("campos[0].minimo");
            assertThat(problemas(campo("a", TipoDeCampo.LISTA_MULTIPLE).opciones("A", "B").minimo("-1")))
                    .containsExactly(Map.entry("campos[0].minimo", "Cuenta opciones elegidas: es un entero, cero o mas."));
            assertThat(problemas(campo("a", TipoDeCampo.LISTA_MULTIPLE).opciones("A", "B").maximo("1.5")))
                    .containsOnlyKeys("campos[0].maximo");
            assertThat(problemas(campo("a", TipoDeCampo.LISTA_MULTIPLE).opciones("A", "B").maximo("2.0")))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Numeros y monedas")
    class Numeros {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TipoDeCampo.class, names = {"NUMERO", "MONEDA"})
        @DisplayName("El minimo no pasa del maximo; iguales, si")
        void minimoYMaximo(TipoDeCampo tipo) {
            assertThat(problemas(campo("a", tipo).minimo("5").maximo("5"))).isEmpty();
            assertThat(problemas(campo("a", tipo).minimo("5.01").maximo("5")))
                    .containsExactly(Map.entry("campos[0].minimo", "El minimo no puede ser mayor que el maximo."));
            assertThat(problemas(campo("a", tipo).minimo("-10"))).isEmpty();
        }

        @Test
        @DisplayName("Un limite cabe en quince cifras enteras y seis decimales; un exponente gigante, no")
        void limitesQueCaben() {
            assertThat(problemas(campo("a", TipoDeCampo.NUMERO).minimo("-999999999999999.999999")
                    .maximo("999999999999999.999999"))).isEmpty();
            assertThat(problemas(campo("a", TipoDeCampo.NUMERO).maximo("1000000000000000")))
                    .containsExactly(Map.entry("campos[0].maximo", "Admite hasta 15 cifras enteras y 6 decimales."));
            assertThat(problemas(campo("a", TipoDeCampo.NUMERO).minimo("0.0000001")))
                    .containsOnlyKeys("campos[0].minimo");
            assertThat(problemas(campo("a", TipoDeCampo.NUMERO).minimo("1E+999999999").maximo("1")))
                    .containsOnlyKeys("campos[0].minimo");
            assertThat(problemas(campo("a", TipoDeCampo.NUMERO).minimo("1.500000000"))).isEmpty();
        }

        @Test
        @DisplayName("La moneda es un codigo ISO 4217 en mayusculas")
        void moneda() {
            assertThat(problemas(campo("a", TipoDeCampo.MONEDA).moneda("EUR"))).isEmpty();
            assertThat(problemas(campo("a", TipoDeCampo.MONEDA).moneda("JPY"))).isEmpty();
            assertThat(problemas(campo("a", TipoDeCampo.MONEDA).moneda("cop")))
                    .containsOnlyKeys("campos[0].moneda");
            assertThat(problemas(campo("a", TipoDeCampo.MONEDA).moneda("XYZ")))
                    .containsExactly(Map.entry("campos[0].moneda", "\"XYZ\" no es un codigo ISO 4217, como COP o EUR."));
        }
    }

    @Nested
    @DisplayName("Los textos")
    class Textos {

        @Test
        @DisplayName("Un texto admite de 1 a 1000 caracteres de largo maximo; uno largo, hasta 10000")
        void topes() {
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).largo(0, 1_000))).isEmpty();
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).largo(null, 1_001)))
                    .containsExactly(Map.entry("campos[0].largoMaximo", "Un campo TEXTO admite de 1 a 1000 caracteres."));
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).largo(null, 0))).containsOnlyKeys("campos[0].largoMaximo");
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO_LARGO).largo(10_000, 10_000))).isEmpty();
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO_LARGO).largo(10_001, null)))
                    .containsExactly(Map.entry("campos[0].largoMinimo", "Va de 0 a 10000 caracteres."));
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).largo(-1, null))).containsOnlyKeys("campos[0].largoMinimo");
        }

        @Test
        @DisplayName("El largo minimo no pasa del maximo; iguales, si")
        void minimoYMaximo() {
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).largo(5, 5))).isEmpty();
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).largo(6, 5))).containsExactly(
                    Map.entry("campos[0].largoMinimo", "El largo minimo no puede ser mayor que el maximo."));
        }

        @Test
        @DisplayName("El patron pasa por RE2/J: uno con vuelta atras no se guarda")
        void patron() {
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).patron("(a)\\1")))
                    .containsOnlyKeys("campos[0].patron");
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO_LARGO).patron("((a{100}){100}){100}")))
                    .containsOnlyKeys("campos[0].patron");
        }
    }

    @Nested
    @DisplayName("Las fechas")
    class Fechas {

        @Test
        @DisplayName("Desde y hasta son dias en una fecha y momentos en una fecha y hora")
        void formato() {
            assertThat(problemas(campo("a", TipoDeCampo.FECHA).entre("2026-01-01", "2026-13-01")))
                    .containsExactly(Map.entry("campos[0].hasta", "Es un dia en ISO 8601, como 2026-12-31."));
            assertThat(problemas(campo("a", TipoDeCampo.FECHA).entre("2026-01-01T00:00:00", null)))
                    .containsOnlyKeys("campos[0].desde");
            assertThat(problemas(campo("a", TipoDeCampo.FECHA_HORA).entre("2026-01-01", null))).containsExactly(
                    Map.entry("campos[0].desde", "Es un dia y una hora en ISO 8601, como 2026-12-31T18:00:00."));
            assertThat(problemas(campo("a", TipoDeCampo.FECHA_HORA).entre(null, "2026-01-01T09:30"))).isEmpty();
        }

        @Test
        @DisplayName("Desde no pasa de hasta; el mismo dia, si")
        void orden() {
            assertThat(problemas(campo("a", TipoDeCampo.FECHA).entre("2026-05-05", "2026-05-05"))).isEmpty();
            assertThat(problemas(campo("a", TipoDeCampo.FECHA).entre("2026-05-06", "2026-05-05"))).containsExactly(
                    Map.entry("campos[0].desde", "La fecha desde no puede ser posterior a la fecha hasta."));
            assertThat(problemas(campo("a", TipoDeCampo.FECHA_HORA)
                    .entre("2026-05-05T10:00:01", "2026-05-05T10:00:00"))).containsOnlyKeys("campos[0].desde");
        }
    }

    @Nested
    @DisplayName("Valores por defecto y condiciones")
    class Condiciones {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"order", "order.total", "_privado", "tarea.revisar.monto", "a.b.c.d.e.f.g.h.i.j"})
        @DisplayName("El valor por defecto es la ruta de una variable del caso")
        void porDefectoBien(String ruta) {
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).porDefecto(ruta))).isEmpty();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"order.", ".order", "order..total", "1order", "order total", "order['x']",
            "a.b.c.d.e.f.g.h.i.j.k"})
        @DisplayName("Lo que no es una ruta no es un valor por defecto")
        void porDefectoMal(String ruta) {
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).porDefecto(ruta)))
                    .containsExactly(Map.entry("campos[0].porDefecto",
                            "Es la ruta de una variable del caso, como order.total."));
        }

        @Test
        @DisplayName("Una condicion que no compila dice lo mismo que el diagnostico de un arco")
        void noCompila() {
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).visibleSi("order.total >")))
                    .containsExactly(Map.entry("campos[0].visibleSi", "Falta con que comparar \"order.total\"."));
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).requeridoSi("x = 1")))
                    .containsOnlyKeys("campos[0].requeridoSi");
        }

        @Test
        @DisplayName("Una condicion en blanco es ninguna")
        void enBlanco() {
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).visibleSi("  ").requeridoSi(""))).isEmpty();
        }

        @Test
        @DisplayName("Hasta mil caracteres")
        void largo() {
            String larga = "x == " + "1".repeat(995);
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).visibleSi(larga))).isEmpty();
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).visibleSi(larga + "1")))
                    .containsExactly(Map.entry("campos[0].visibleSi", "No puede superar 1000 caracteres."));
        }

        @Test
        @DisplayName("Una condicion ve los campos de arriba: ni el suyo ni los de abajo")
        void soloLosDeArriba() {
            assertThat(problemas(campo("metodo", TipoDeCampo.LISTA).opciones("A"),
                    campo("numero", TipoDeCampo.TEXTO).visibleSi("metodo == 'A'"))).isEmpty();
            assertThat(problemas(campo("numero", TipoDeCampo.TEXTO).visibleSi("metodo == 'A'"),
                    campo("metodo", TipoDeCampo.LISTA).opciones("A"))).containsExactly(Map.entry(
                    "campos[0].visibleSi", "Pregunta por \"metodo\", que no va antes que este campo: una condicion solo "
                            + "ve los campos de arriba."));
            assertThat(problemas(campo("numero", TipoDeCampo.NUMERO).requeridoSi("numero > 1")))
                    .containsOnlyKeys("campos[0].requeridoSi");
        }

        @Test
        @DisplayName("Una ruta que sigue por dentro de un campo se mira por su primer nombre")
        void primerTramo() {
            assertThat(problemas(campo("extras", TipoDeCampo.TEXTO).visibleSi("pedido.extras == 'A'"),
                    campo("pedido", TipoDeCampo.TEXTO))).containsOnlyKeys("campos[0].visibleSi");
        }

        @Test
        @DisplayName("Una seccion no tiene valor por el que preguntar")
        void porUnaSeccion() {
            assertThat(problemas(campo("datos", TipoDeCampo.SECCION),
                    campo("a", TipoDeCampo.TEXTO).visibleSi("datos == 'x'"))).containsExactly(Map.entry(
                    "campos[1].visibleSi", "\"datos\" es una seccion: no tiene valor por el que preguntar."));
        }

        @Test
        @DisplayName("Lo que no es un campo es una variable del caso, y de esas no se sabe nada al guardar")
        void variablesDelCaso() {
            assertThat(problemas(campo("a", TipoDeCampo.TEXTO).visibleSi("order.total > 100 and caso.referencia != 'X'")))
                    .isEmpty();
        }

        @Test
        @DisplayName("Con varios problemas en un campo, cada propiedad dice el suyo")
        void varios() {
            assertThat(problemas(campo("1", TipoDeCampo.TEXTO).etiqueta("").largo(9, 3).visibleSi("(")))
                    .containsOnlyKeys("campos[0].nombre", "campos[0].etiqueta", "campos[0].largoMinimo",
                            "campos[0].visibleSi");
        }
    }
}
