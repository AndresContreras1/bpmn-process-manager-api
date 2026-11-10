package com.facimus.procesos.modelado.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.facimus.procesos.common.EntidadEmpresa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * PR 52: un BPMNEdge. Por donde pasa un flujo de secuencia o de mensaje, punto a punto, y donde va su etiqueta. La
 * calcula {@link DisposicionBpmn} con cada cambio del proceso.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "aristas_bpmn")
public class AristaBpmn extends EntidadEmpresa {

    private static final Pattern PUNTO = Pattern.compile("\\[(-?[0-9.]+),(-?[0-9.]+)]");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proceso_id", nullable = false)
    private Long procesoId;

    /** El bpmn_id del flujo: el bpmnElement del BPMNEdge. */
    @Column(nullable = false, length = 64)
    private String elemento;

    /** SECUENCIA o MENSAJE. */
    @Column(nullable = false, length = 20)
    private String tipo;

    /** Los puntos en orden, como [[x,y],...]. */
    @Column(nullable = false, columnDefinition = "text")
    private String puntos;

    @Column(name = "etiqueta_x")
    private Double etiquetaX;

    @Column(name = "etiqueta_y")
    private Double etiquetaY;

    @Column(name = "etiqueta_ancho")
    private Double etiquetaAncho;

    @Column(name = "etiqueta_alto")
    private Double etiquetaAlto;

    /** Los puntos como los guarda la base. */
    public static String escribir(List<DisposicionBpmn.Punto> puntos) {
        StringBuilder texto = new StringBuilder("[");
        for (DisposicionBpmn.Punto punto : puntos) {
            if (texto.length() > 1) {
                texto.append(',');
            }
            texto.append(String.format(Locale.ROOT, "[%s,%s]", numero(punto.x()), numero(punto.y())));
        }
        return texto.append(']').toString();
    }

    public List<DisposicionBpmn.Punto> leerPuntos() {
        List<DisposicionBpmn.Punto> leidos = new ArrayList<>();
        Matcher encontrado = PUNTO.matcher(puntos);
        while (encontrado.find()) {
            leidos.add(new DisposicionBpmn.Punto(Double.parseDouble(encontrado.group(1)),
                    Double.parseDouble(encontrado.group(2))));
        }
        return leidos;
    }

    /** Sin el .0 de los enteros, que son casi todos. */
    private static String numero(double valor) {
        return valor == Math.rint(valor) ? Long.toString((long) valor) : Double.toString(valor);
    }
}
