package com.facimus.procesos.config;

import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Crea una empresa y un administrador de demostracion en el primer arranque,
 * unicamente si la base de datos esta vacia, para poder iniciar sesion sin
 * pasar antes por el formulario de registro.
 */
@Component
@RequiredArgsConstructor
public class DatosDemoInitializer implements CommandLineRunner {

    private static final String NIT_DEMO = "900123456-1";
    private static final String EMAIL_DEMO = "admin@demo.com";
    private static final String PASSWORD_DEMO = "admin123";

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (empresaRepository.count() > 0) {
            return;
        }

        Empresa empresa = new Empresa();
        empresa.setNombre("Empresa Demo S.A.S.");
        empresa.setNit(NIT_DEMO);
        empresa.setCorreoContacto("contacto@demo.com");
        empresa.setFechaRegistro(LocalDate.now());
        empresa = empresaRepository.save(empresa);

        Usuario admin = new Usuario();
        admin.setEmpresa(empresa);
        admin.setNombre("Administrador Demo");
        admin.setEmail(EMAIL_DEMO);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD_DEMO));
        admin.setRolAcceso(RolAcceso.ADMINISTRADOR);
        admin.setActivo(true);
        usuarioRepository.save(admin);
    }
}
