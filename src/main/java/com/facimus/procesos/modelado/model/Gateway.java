package com.facimus.procesos.modelado.model;

import org.hibernate.annotations.SQLDelete;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** Un punto de decision o ramificacion: exclusiva, paralela o inclusiva. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@DiscriminatorValue("GATEWAY")
// El SQL de borrado no se hereda: cada tipo de nodo marca su fila como inactiva.
@SQLDelete(sql = "update nodos_flujo set activo = false where id = ? and version = ?")
public class Gateway extends NodoFlujo {

    // Sin nullable = false: con SINGLE_TABLE las actividades comparten esta columna y no tienen tipo.
    // Que todo gateway tenga tipo lo exigen GatewayRequest (@NotNull) y el check ck_nodos_flujo_gateway_con_tipo.
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_gateway", length = 20)
    private TipoGateway tipoGateway;

    @Override
    public boolean exigeCondicionAlSalir() {
        return tipoGateway.eligePorCondicion();
    }
}
