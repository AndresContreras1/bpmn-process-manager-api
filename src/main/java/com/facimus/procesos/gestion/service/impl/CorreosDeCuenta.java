package com.facimus.procesos.gestion.service.impl;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.correo.Correo;
import com.facimus.procesos.common.correo.Redactor;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.trabajos.ManejadorDeTrabajo;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.gestion.model.EnlaceDeUnUso;
import com.facimus.procesos.gestion.model.PropositoDeEnlace;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.EnlaceDeUnUsoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Manda los correos de la cuenta que esperan en la cola. El enlace se crea aqui, al mandarlo, y en la misma
 * transaccion: si el servidor de correo no lo acepta, el enlace tampoco queda, y el reintento crea otro. El token en
 * claro solo existe en el correo; la base guarda su SHA-256.
 *
 * <p>El enlace lleva el token despues de {@code #}: el navegador no manda esa parte al servidor, asi que el token no
 * queda en el registro de NGINX ni de nadie.
 */
@Component
public class CorreosDeCuenta implements ManejadorDeTrabajo {

    /** 256 bits aleatorios: adivinar un enlace no es viable. */
    private static final int BYTES_DEL_TOKEN = 32;

    private final JsonMapper jsonMapper;
    private final EnlaceDeUnUsoRepository enlaceRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final Redactor redactor;
    private final Correo correo;
    private final Clock reloj;
    private final String web;
    private final SecureRandom aleatorio = new SecureRandom();

    public CorreosDeCuenta(JsonMapper jsonMapper, EnlaceDeUnUsoRepository enlaceRepository,
            UsuarioRepository usuarioRepository, EmpresaRepository empresaRepository, Redactor redactor,
            Correo correo, Clock reloj, @Value("${app.url-publica}") String web) {
        this.jsonMapper = jsonMapper;
        this.enlaceRepository = enlaceRepository;
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.redactor = redactor;
        this.correo = correo;
        this.reloj = reloj;
        this.web = web.endsWith("/") ? web.substring(0, web.length() - 1) : web;
    }

    @Override
    public String tipo() {
        return PedidoDeCorreo.TIPO;
    }

    @Override
    @Transactional
    public void ejecutar(Trabajo trabajo) {
        PedidoDeCorreo pedido = jsonMapper.readValue(trabajo.datos(), PedidoDeCorreo.class);
        PropositoDeEnlace proposito = pedido.proposito();
        Usuario usuario = pedido.usuarioId() == null ? null
                : usuarioRepository.findByIdAndEmpresaId(pedido.usuarioId(), pedido.empresaId()).orElse(null);
        if (yaNoHaceFalta(proposito, usuario, pedido)) {
            return;
        }
        Empresa empresa = empresaRepository.findById(pedido.empresaId()).orElseThrow();
        String token = tokenNuevo();
        LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
        enlaceRepository.save(EnlaceDeUnUso.builder()
                .empresa(empresa)
                .usuario(usuario)
                .email(pedido.email())
                .proposito(proposito)
                .rolAcceso(pedido.rolAcceso())
                .tokenHash(Huella.de(token))
                .creadoEn(ahora)
                .venceEn(ahora.plus(proposito.vigencia()))
                .build());

        Map<String, Object> datos = new HashMap<>();
        datos.put("enlace", web + proposito.pantalla() + "#token=" + token);
        datos.put("horas", proposito.vigencia().toHours());
        datos.put("minutos", proposito.vigencia().toMinutes());
        datos.put("dias", proposito.vigencia().toDays());
        datos.put("tienda", empresa.getNombre());
        if (usuario != null) {
            datos.put("nombre", usuario.getNombre());
        }
        if (pedido.autorId() != null) {
            usuarioRepository.findByIdAndEmpresaId(pedido.autorId(), pedido.empresaId())
                    .ifPresent(autor -> datos.put("quienInvita", autor.getNombre()));
        }
        correo.enviar(redactor.redactar(proposito.plantilla(), Locale.of(pedido.idioma()), pedido.email(), datos));
    }

    /**
     * Lo que paso mientras el correo esperaba en la cola: el usuario se desactivo, o ya verifico el correo por otro
     * enlace. Una invitacion no tiene usuario, y siempre sale.
     */
    private static boolean yaNoHaceFalta(PropositoDeEnlace proposito, Usuario usuario, PedidoDeCorreo pedido) {
        if (proposito == PropositoDeEnlace.INVITACION) {
            return false;
        }
        return usuario == null || !usuario.isActivo()
                || proposito == PropositoDeEnlace.VERIFICAR_CORREO && usuario.getCorreoVerificadoEn() != null
                || !usuario.getEmail().equals(pedido.email());
    }

    private String tokenNuevo() {
        byte[] bytes = new byte[BYTES_DEL_TOKEN];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
