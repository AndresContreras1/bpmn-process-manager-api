package com.facimus.procesos.gestion.model;

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

/**
 * D16: lo que cada tienda decide sobre si misma: quien puede tocar la estructura de los diagramas y, desde que hay
 * ejecucion, el reloj de su simulacion y quien lo mueve. Una fila por tienda, creada con ella.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "configuracion_tienda")
public class ConfiguracionTienda extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_estructura", nullable = false, length = 30)
    @Builder.Default
    private PoliticaEstructura politicaEstructura = PoliticaEstructura.ADMINISTRADOR_Y_EDITOR;

    /**
     * D8: el reloj de la simulacion de esta tienda, en ticks. Empieza en cero y solo sube; lo mueve quien prueba.
     * No es una hora: es el numero de pasos que la tienda ha dado, y con el se fecha todo lo que pasa en un caso.
     */
    @Column(nullable = false)
    @Builder.Default
    private int reloj = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_simulacion", nullable = false, length = 20)
    @Builder.Default
    private ModoSimulacion modoSimulacion = ModoSimulacion.MANUAL;
}
