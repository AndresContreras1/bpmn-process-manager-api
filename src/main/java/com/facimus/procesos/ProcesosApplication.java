package com.facimus.procesos;

import java.time.ZoneOffset;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProcesosApplication {

    public static void main(String[] args) {
        // F12: cada fecha que guarda y responde la API esta en UTC, sea cual sea la maquina; la web la pasa a la zona
        // horaria de quien la mira. El reloj de la API ya es Clock.systemUTC(), y esto alcanza tambien a now().
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
        SpringApplication.run(ProcesosApplication.class, args);
    }
}
