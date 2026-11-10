package com.facimus.procesos.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.common.xml.SecureXml;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

/** PR 52: la regla de {@link XmlSeguroTest} de verdad ve una factoria de XML, y no pasa por no mirar. */
class XmlSeguroReglaTest {

    @Test
    @DisplayName("La regla ve las factorias: aplicada a SecureXml, sin su excepcion, falla")
    void laReglaVeLasFactorias() {
        JavaClasses secureXml = new ClassFileImporter().importClasses(SecureXml.class);
        ArchRule sinExcepcion = noClasses().should().callCodeUnitWhere(XmlSeguroTest.CREAR_UNA_FACTORIA_XML);

        assertThatThrownBy(() -> sinExcepcion.check(secureXml))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("DocumentBuilderFactory.newInstance")
                .hasMessageContaining("XMLInputFactory.newFactory");
    }
}
