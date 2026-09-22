package com.facimus.procesos.common;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Una entidad de la empresa que los usuarios editan. Su version sube con cada cambio guardado: quien edita manda la
 * version que leyo, y si otra persona guardo antes, la edicion responde 409 en vez de pisar ese cambio. La auditoria
 * de Spring Data anota quien la creo y quien guardo el ultimo cambio, y cuando.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class EntidadEditable extends EntidadEmpresa {

    /** La maneja Hibernate: la sube al guardar y rechaza el guardado si la fila ya tenia otra. */
    @Version
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private Long version;

    /** El usuario que la creo; vacio si la creo el sistema, como el registro de una tienda o la semilla de dev. */
    @CreatedBy
    @Setter(AccessLevel.NONE)
    @Column(name = "creado_por", updatable = false)
    private Long creadoPor;

    @CreatedDate
    @Setter(AccessLevel.NONE)
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    /** El usuario que guardo el ultimo cambio. */
    @LastModifiedBy
    @Setter(AccessLevel.NONE)
    @Column(name = "modificado_por")
    private Long modificadoPor;

    @LastModifiedDate
    @Setter(AccessLevel.NONE)
    @Column(name = "fecha_modificacion", nullable = false)
    private LocalDateTime fechaModificacion;

    /** La version que el cliente leyo tiene que ser la guardada; si no, otra persona cambio la entidad despues. */
    public void verificarVersion(Long versionLeida) {
        if (!Objects.equals(version, versionLeida)) {
            throw new ConflictoDeVersionException(versionLeida, version);
        }
    }
}
