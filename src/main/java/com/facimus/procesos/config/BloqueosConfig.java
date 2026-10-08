package com.facimus.procesos.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/**
 * D34: con varias instancias de la API, cada trabajo programado corre en una sola a la vez. Antes de correr, toma su
 * candado en la tabla shedlock; la otra instancia lo encuentra tomado y se salta esa vuelta. La hora es la de
 * PostgreSQL y no la de cada maquina, asi que dos relojes que no coinciden no dan dos candados.
 *
 * <p>Cada metodo {@code @Scheduled} lleva su {@code @SchedulerLock}, y una regla de ArchUnit lo exige.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class BloqueosConfig {

    @Bean
    public LockProvider proveedorDeCandados(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
