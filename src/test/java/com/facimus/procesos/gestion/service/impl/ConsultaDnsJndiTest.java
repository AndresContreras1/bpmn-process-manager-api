package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PR 50: los TXT llegan como los escribe el DNS; la verificacion los compara como los escribio la tienda. */
class ConsultaDnsJndiTest {

    @Test
    @DisplayName("Un TXT en trozos entre comillas se une, y uno sin comillas queda igual")
    void unir() {
        assertThat(ConsultaDnsJndi.unir("\"bpmn-process-manager-verificacion=\" \"6f1d0c3a\""))
                .isEqualTo("bpmn-process-manager-verificacion=6f1d0c3a");
        assertThat(ConsultaDnsJndi.unir("\"v=spf1 -all\"")).isEqualTo("v=spf1 -all");
        assertThat(ConsultaDnsJndi.unir(" v=spf1 -all ")).isEqualTo("v=spf1 -all");
    }

    @Test
    @DisplayName("Un dominio que no existe no tiene registros, sin lanzar nada")
    void dominioQueNoExiste() {
        assertThat(new ConsultaDnsJndi().registrosTxt("no-existe.invalid")).isEmpty();
    }
}
