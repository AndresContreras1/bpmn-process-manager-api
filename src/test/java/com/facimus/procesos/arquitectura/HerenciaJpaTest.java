package com.facimus.procesos.arquitectura;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import com.facimus.procesos.modelado.model.NodoFlujo;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Decisiones de herencia JPA y mapeo de enums del consolidado de arquitectura.
 */
class HerenciaJpaTest {

    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.facimus.procesos");
    }

    @Test
    @DisplayName("NodoFlujo usa @Inheritance(SINGLE_TABLE)")
    void nodoFlujo_usa_single_table() {
        classes()
                .that().haveSimpleName("NodoFlujo")
                .should(tenerInheritanceSingleTable())
                .because("Consolidado: NodoFlujo usa SINGLE_TABLE, 2 subtipos con pocos campos")
                .check(clases);
    }

    @Test
    @DisplayName("Actividad y Gateway extienden NodoFlujo")
    void subtipos_extienden_NodoFlujo() {
        classes()
                .that().haveSimpleNameStartingWith("Actividad")
                .or().haveSimpleNameStartingWith("Gateway")
                .and().resideInAPackage("..model..")
                .and().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().beAssignableTo(NodoFlujo.class)
                .because("Actividad y Gateway son los unicos subtipos de NodoFlujo")
                .check(clases);
    }

    @Test
    @DisplayName("Todos los @Enumerated usan EnumType.STRING, nunca ORDINAL")
    void enums_mapeados_como_string() {
        classes()
                .that().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should(usarEnumTypeString())
                .because("Consolidado: enums siempre STRING para evitar corrupcion por reordenamiento")
                .check(clases);
    }

    private static ArchCondition<JavaClass> tenerInheritanceSingleTable() {
        return new ArchCondition<>("tener @Inheritance(SINGLE_TABLE)") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                javaClass.tryGetAnnotationOfType(Inheritance.class).ifPresentOrElse(
                        inheritance -> {
                            if (inheritance.strategy() != InheritanceType.SINGLE_TABLE) {
                                events.add(SimpleConditionEvent.violated(javaClass,
                                        javaClass.getName() + " usa " + inheritance.strategy()
                                                + " en vez de SINGLE_TABLE"));
                            }
                        },
                        () -> events.add(SimpleConditionEvent.violated(javaClass,
                                javaClass.getName() + " no tiene @Inheritance"))
                );
            }
        };
    }

    private static ArchCondition<JavaClass> usarEnumTypeString() {
        return new ArchCondition<>("usar @Enumerated(STRING) en todos los campos enum") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                javaClass.getAllFields().stream()
                        .filter(f -> f.tryGetAnnotationOfType(Enumerated.class).isPresent())
                        .forEach(f -> {
                            Enumerated ann = f.tryGetAnnotationOfType(Enumerated.class).get();
                            if (ann.value() != EnumType.STRING) {
                                events.add(SimpleConditionEvent.violated(f,
                                        f.getFullName() + " usa EnumType." + ann.value()
                                                + " en vez de STRING"));
                            }
                        });
            }
        };
    }
}
