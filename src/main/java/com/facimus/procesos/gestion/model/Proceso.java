package com.facimus.procesos.gestion.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.facimus.procesos.common.EntidadEditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "procesos")
public class Proceso extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 4000)
    private String descripcion;

    @Column(nullable = false, length = 80)
    private String categoria;

    /** PR 55: si se ejecuta o solo se documenta. Los que ya existian, y los nuevos, son ejecutables. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ModoProceso modo = ModoProceso.EJECUTABLE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoProceso estado = EstadoProceso.BORRADOR;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    /** Numero de la version vigente. Vacio si nunca se publico, o si se retiraron todas las que tenia. */
    @Column(name = "version_publicada")
    private Integer versionPublicada;

    /** Huella de esa version. Comparada con la del modelo vivo dice si el borrador tiene cambios sin publicar. */
    @Column(name = "huella_publicada", length = 64)
    private String huellaPublicada;

    /** PR 59: la carpeta donde esta archivado; vacia si no se archivo. */
    @Column(name = "carpeta_id")
    private Long carpetaId;

    /** PR 59: el usuario que responde por el proceso. */
    @Column(name = "dueno_id")
    private Long duenoId;

    @Column(name = "proxima_revision")
    private LocalDate proximaRevision;

    /** PR 64: cuanto mueve la proxima revision confirmar que el proceso sigue vigente. */
    @Column(name = "meses_entre_revisiones", nullable = false)
    @Builder.Default
    private int mesesEntreRevisiones = 12;

    /**
     * PR 64: la fecha de revision por la que ya se le aviso al dueno que llego, y por la que ya se le aviso que vencio.
     * Las escribe el aviso diario con una consulta propia, que no cuenta como una edicion del proceso; guardar el
     * proceso no las toca, asi que una edicion que se cruce con el aviso no le devuelve un valor viejo.
     */
    @Column(name = "revision_avisada", insertable = false, updatable = false)
    private LocalDate revisionAvisada;

    @Column(name = "vencida_avisada", insertable = false, updatable = false)
    private LocalDate vencidaAvisada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Clasificacion clasificacion = Clasificacion.INTERNA;

    /** En un arreglo de la misma fila: se leen con el proceso, sin una consulta mas por cada uno de la lista. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "varchar(40)[]")
    @Builder.Default
    private List<String> etiquetas = new ArrayList<>();

    /** PR 64: la fecha de revision paso sin que nadie confirmara que el proceso sigue vigente. */
    public boolean revisionVencida(LocalDate hoy) {
        return proximaRevision != null && proximaRevision.isBefore(hoy);
    }
}
