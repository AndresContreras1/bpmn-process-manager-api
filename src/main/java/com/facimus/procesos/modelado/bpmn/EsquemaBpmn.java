package com.facimus.procesos.modelado.bpmn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.facimus.procesos.common.xml.SecureXml;

/**
 * PR 53: el esquema de BPMN 2.0.2 de la OMG ({@code bpmn/xsd}, bajado de omg.org/spec/BPMN/20100501) junto con el de
 * las extensiones {@code bpm:}. Lo que se valida tiene que haber pasado antes por {@link SecureXml#comprobar}: el
 * validador lee el documento entero.
 */
public final class EsquemaBpmn {

    private EsquemaBpmn() {
    }

    /** Lo que el documento incumple del esquema, con su linea; vacio si valida. */
    public static List<String> problemas(byte[] xml) {
        List<String> problemas = new ArrayList<>();
        Validator validador = SecureXml.validador(Cargado.ESQUEMA);
        validador.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException aviso) {
                problemas.add(linea(aviso));
            }

            @Override
            public void error(SAXParseException error) {
                problemas.add(linea(error));
            }

            @Override
            public void fatalError(SAXParseException error) {
                problemas.add(linea(error));
            }
        });
        try {
            validador.validate(new StreamSource(new ByteArrayInputStream(xml)));
        } catch (SAXException e) {
            // Un error fatal ya quedo anotado por el manejador
            if (problemas.isEmpty()) {
                problemas.add(e.getMessage());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return problemas;
    }

    private static String linea(SAXParseException problema) {
        return "Linea " + problema.getLineNumber() + ": " + problema.getMessage();
    }

    /** El esquema se carga la primera vez que se necesita: son cinco archivos y una compilacion. */
    private static final class Cargado {

        private static final Schema ESQUEMA = SecureXml.esquemaDelClasspath("bpmn/xsd", "BPMN20.xsd", "bpm.xsd");

        private Cargado() {
        }
    }
}
