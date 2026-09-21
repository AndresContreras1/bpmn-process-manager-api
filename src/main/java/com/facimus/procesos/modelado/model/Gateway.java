package com.facimus.procesos.modelado.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un punto de decision o ramificacion: exclusiva, paralela o inclusiva. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@DiscriminatorValue("GATEWAY")
public class Gateway extends NodoFlujo {

    // Sin nullable = false: con SINGLE_TABLE las actividades comparten esta columna y no tienen tipo.
    // El tipo del gateway lo exige GatewayRequest con @NotNull.
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_gateway")
    private TipoGateway tipoGateway;
}
