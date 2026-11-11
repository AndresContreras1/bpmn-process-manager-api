package com.facimus.procesos.modelado.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * PR 54: lo que un archivo BPMN de otra herramienta traia para un elemento y el modelo no tiene: sus atributos y sus
 * {@code extensionElements} de otros espacios de nombres, como los de Camunda. Se guarda saneado, en un envoltorio
 * propio, y vuelve al exportar en el mismo elemento (D25).
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "extension_xml")
public class ExtensionXml extends EntidadEmpresa {

    /** La clave de lo que no es un elemento con bpmn_id: las definiciones y la colaboracion del archivo. */
    public static final String DEFINICIONES = "#definitions";
    public static final String COLABORACION = "#collaboration";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proceso_id", nullable = false)
    private Long procesoId;

    /**
     * El bpmn_id del elemento, una de las claves de arriba, {@code <pool>#process} para el proceso de un pool o
     * {@code <elemento>#di} para su forma o su arista.
     */
    @Column(nullable = false, length = 80)
    private String elemento;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String xml;

    /** La clave del proceso de un pool, que el modelo no guarda como elemento. */
    public static String delProceso(String pool) {
        return pool + "#process";
    }

    /** La clave de la forma o la arista de un elemento: los colores de bpmn.io, por ejemplo, van en el dibujo. */
    public static String delDibujo(String elemento) {
        return elemento + "#di";
    }
}
