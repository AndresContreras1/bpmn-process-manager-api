package com.facimus.procesos.arquitectura;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import org.springframework.modulith.docs.Documenter.DiagramOptions;
import org.springframework.modulith.docs.Documenter.Options;

import com.facimus.procesos.ProcesosApplication;
import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Los modulos de la aplicacion vistos por Spring Modulith, ademas de las reglas de ArchUnit: sin ciclos, y cada uno
 * usando de otro solo lo que ese otro expone y solo si lo declara como dependencia. config queda fuera: es donde se
 * arma todo, y por eso puede tocar cualquier cosa.
 *
 * <p>La documentacion de docs/modules sale de aqui. Si un modulo cambia, la prueba falla hasta que se regenera con
 * {@code ./mvnw test -Dtest=ModulosTest -Dmodulos.documentar=true}.
 */
class ModulosTest {

    static final ApplicationModules MODULOS = ApplicationModules.of(ProcesosApplication.class,
            JavaClass.Predicates.resideInAPackage("com.facimus.procesos.config.."));

    private static final Path PUBLICADA = Path.of("docs", "modules");

    @Test
    @DisplayName("Los modulos respetan sus fronteras: sin ciclos, sin tocar lo interno de otro y solo los declarados")
    void losModulosRespetanSusFronteras() {
        MODULOS.verify();
    }

    @Test
    @DisplayName("La documentacion de docs/modules es la de los modulos de hoy")
    void laDocumentacionDeLosModulosEstaAlDia() {
        boolean documentar = Boolean.getBoolean("modulos.documentar");
        Path destino = documentar ? PUBLICADA : Path.of("target", "modulos");
        new Documenter(MODULOS, Options.defaults().withOutputFolder(destino.toString()))
                .writeModulesAsPlantUml(DiagramOptions.defaults())
                .writeIndividualModulesAsPlantUml(DiagramOptions.defaults())
                .writeModuleCanvases();

        for (Path generado : archivos(destino)) {
            Path publicado = PUBLICADA.resolve(destino.relativize(generado));
            assertThat(publicado).as("falta %s en docs/modules", publicado.getFileName()).exists();
            assertThat(lineas(publicado)).as("%s no es la de los modulos de hoy", publicado.getFileName())
                    .isEqualTo(lineas(generado));
        }
    }

    private static List<Path> archivos(Path carpeta) {
        try (Stream<Path> todos = Files.walk(carpeta)) {
            return todos.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Las lineas, ordenadas: Modulith no siempre escribe las relaciones en el mismo orden, y el orden no cambia lo que
     * dicen. Tampoco importan los finales de linea, que en Windows Git cambia al traer el repositorio.
     */
    private static List<String> lineas(Path archivo) {
        try {
            return Files.readString(archivo).lines().sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
