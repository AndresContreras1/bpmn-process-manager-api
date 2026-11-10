package com.facimus.procesos.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Set;

import com.facimus.procesos.common.xml.SecureXml;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * PR 52: SecureXml es la unica que crea parsers de XML. Una factoria creada en otra parte nace con los DTD, las
 * entidades externas y XInclude a merced de quien manda el documento.
 */
@AnalyzeClasses(packages = "com.facimus.procesos", importOptions = ImportOption.DoNotIncludeTests.class)
class XmlSeguroTest {

    private static final Set<String> FACTORIAS = Set.of(
            "javax.xml.stream.XMLInputFactory",
            "javax.xml.parsers.DocumentBuilderFactory",
            "javax.xml.parsers.SAXParserFactory",
            "javax.xml.transform.TransformerFactory",
            "javax.xml.validation.SchemaFactory",
            "javax.xml.xpath.XPathFactory");

    static final DescribedPredicate<JavaCall<?>> CREAR_UNA_FACTORIA_XML = new DescribedPredicate<>(
            "crear una factoria de XML") {
        @Override
        public boolean test(JavaCall<?> llamada) {
            String metodo = llamada.getTarget().getName();
            return FACTORIAS.contains(llamada.getTargetOwner().getFullName()) && metodo.startsWith("new")
                    && (metodo.endsWith("Instance") || metodo.endsWith("Factory"));
        }
    };

    @ArchTest
    static final ArchRule soloSecureXmlCreaParsers = noClasses()
            .that().doNotHaveFullyQualifiedName(SecureXml.class.getName())
            .should().callCodeUnitWhere(CREAR_UNA_FACTORIA_XML)
            .because("PR 52: un parser sin endurecer lee DTD, entidades externas y XInclude; SecureXml es la puerta");
}
