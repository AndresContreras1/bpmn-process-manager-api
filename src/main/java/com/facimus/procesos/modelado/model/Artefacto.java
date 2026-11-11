package com.facimus.procesos.modelado.model;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.facimus.procesos.common.EntidadEditable;
import com.facimus.procesos.gestion.model.Proceso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * PR 55: una anotacion, un grupo, un objeto de datos o un almacen. Explica el proceso sin ser parte de su flujo. En
 * una lane, su posicion es relativa a ella, como la de un nodo; sin lane, es la del dibujo. Un objeto de datos o un
 * almacen va siempre en un pool modelado, que es donde BPMN los pone.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "artefactos")
@SQLDelete(sql = "update artefactos set activo = false where id = ? and version = ?")
@SQLRestriction("activo = true")
public class Artefacto extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bpmn_id", nullable = false, updatable = false, length = 64)
    private String bpmnId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoArtefacto tipo;

    /** El texto de una anotacion, o el nombre de lo demas. */
    @Column(length = 1000)
    private String texto;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_id")
    private Pool pool;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lane_id")
    private Lane lane;

    @Column(name = "posicion_x", nullable = false)
    private int posicionX;

    @Column(name = "posicion_y", nullable = false)
    private int posicionY;

    @Column(nullable = false)
    private int ancho;

    @Column(nullable = false)
    private int alto;

    @PrePersist
    void asignarBpmnId() {
        if (bpmnId == null) {
            bpmnId = BpmnId.nuevo(prefijoBpmn(tipo));
        }
    }

    /** El prefijo del id de cada tipo, el mismo que les da bpmn.io. */
    public static String prefijoBpmn(TipoArtefacto tipo) {
        return switch (tipo) {
            case ANOTACION -> "TextAnnotation";
            case GRUPO -> "Group";
            case OBJETO_DATOS -> "DataObjectReference";
            case ALMACEN_DATOS -> "DataStoreReference";
        };
    }

    /** El tamano con el que se dibuja si no se dice otro, el de bpmn.io. */
    public static int[] tamanoPorDefecto(TipoArtefacto tipo) {
        return switch (tipo) {
            case ANOTACION -> new int[] {100, 30};
            case GRUPO -> new int[] {300, 200};
            case OBJETO_DATOS -> new int[] {36, 50};
            case ALMACEN_DATOS -> new int[] {50, 50};
        };
    }
}
