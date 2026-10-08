package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * PR 35: las sentencias del borrado de una tienda, contra el catalogo de la base que dejan las migraciones. Una tabla
 * nueva con {@code empresa_id} que no este en la lista, o una clave foranea que la lista borre al reves, rompe esta
 * prueba antes de dejar filas sueltas o de hacer fallar la purga de una noche.
 */
@SpringBootTest
@ActiveProfiles("test")
class BorradoDeTiendasTest {

    private static final Pattern TABLA = Pattern.compile("^delete from (\\w+) where (\\w+) = \\?$");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Hay una sentencia por cada tabla con empresa_id, y cada una borra por esa columna")
    void cubreCadaTablaDeLaTienda() {
        List<String> conEmpresa = jdbc.queryForList("select table_name from information_schema.columns "
                + "where table_schema = current_schema() and column_name = 'empresa_id' and table_name <> 'empresas'",
                String.class);

        List<String> porEmpresa = BorradoDeTiendas.BORRADOS.stream()
                .map(BorradoDeTiendasTest::partes)
                .filter(partes -> partes.group(2).equals("empresa_id"))
                .map(partes -> partes.group(1))
                .toList();

        assertThat(porEmpresa).containsExactlyInAnyOrderElementsOf(conEmpresa).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Ninguna tabla se borra antes que las que apuntan a ella")
    void respetaLasClavesForaneas() {
        List<Map<String, Object>> claves = jdbc.queryForList("""
                select distinct tc.table_name as hija, ccu.table_name as madre
                from information_schema.table_constraints tc
                join information_schema.constraint_column_usage ccu
                  on ccu.constraint_schema = tc.constraint_schema and ccu.constraint_name = tc.constraint_name
                where tc.constraint_type = 'FOREIGN KEY' and tc.table_schema = current_schema()
                """);
        List<String> orden = BorradoDeTiendas.BORRADOS.stream().map(sentencia -> partes(sentencia).group(1)).toList();

        assertThat(claves).isNotEmpty().allSatisfy(clave -> {
            String hija = (String) clave.get("hija");
            String madre = (String) clave.get("madre");
            if (!hija.equals(madre) && orden.contains(hija) && orden.contains(madre)) {
                assertThat(orden.lastIndexOf(hija)).as("%s apunta a %s", hija, madre)
                        .isLessThan(orden.indexOf(madre));
            }
        });
    }

    private static Matcher partes(String sentencia) {
        Matcher partes = TABLA.matcher(sentencia);
        assertThat(partes.matches()).as(sentencia).isTrue();
        return partes;
    }
}
