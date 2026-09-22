package com.facimus.procesos.common;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Una entidad de la empresa que los usuarios editan. Su version sube con cada cambio guardado: quien edita manda la
 * version que leyo, y si otra persona guardo antes, la edicion responde 409 en vez de pisar ese cambio.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@MappedSuperclass
public abstract class EntidadEditable extends EntidadEmpresa {

    /** La maneja Hibernate: la sube al guardar y rechaza el guardado si la fila ya tenia otra. */
    @Version
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private Long version;

    /** La version que el cliente leyo tiene que ser la guardada; si no, otra persona cambio la entidad despues. */
    public void verificarVersion(Long versionLeida) {
        if (!Objects.equals(version, versionLeida)) {
            throw new ConflictoDeVersionException(versionLeida, version);
        }
    }
}
