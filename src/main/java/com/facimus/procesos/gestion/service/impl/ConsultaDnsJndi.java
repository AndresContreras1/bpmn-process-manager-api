package com.facimus.procesos.gestion.service.impl;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.springframework.stereotype.Component;

import com.facimus.procesos.gestion.service.ConsultaDns;

/**
 * PR 50: los TXT con el resolvedor de DNS que trae el JDK, sin dependencias. Espera dos segundos por servidor y
 * reintenta una vez; un dominio que no existe o no responde no tiene registros. El dominio llega validado por el
 * patron de DominioRequest, asi que no lleva nada que JNDI interprete.
 */
@Component
public class ConsultaDnsJndi implements ConsultaDns {

    @Override
    @SuppressWarnings("java:S1149") // JNDI pide un Hashtable para su entorno
    public List<String> registrosTxt(String dominio) {
        Hashtable<String, String> entorno = new Hashtable<>();
        entorno.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        entorno.put("com.sun.jndi.dns.timeout.initial", "2000");
        entorno.put("com.sun.jndi.dns.timeout.retries", "1");
        try {
            DirContext contexto = new InitialDirContext(entorno);
            try {
                Attribute txt = contexto.getAttributes(dominio, new String[] {"TXT"}).get("TXT");
                List<String> registros = new ArrayList<>();
                if (txt != null) {
                    NamingEnumeration<?> valores = txt.getAll();
                    while (valores.hasMore()) {
                        registros.add(unir(String.valueOf(valores.next())));
                    }
                }
                return registros;
            } finally {
                contexto.close();
            }
        } catch (NamingException e) {
            return List.of();
        }
    }

    /** Un TXT largo llega en trozos entre comillas ("a" "b"): se unen, como hace quien lo publica. */
    static String unir(String registro) {
        String sinEspacios = registro.strip();
        if (!sinEspacios.startsWith("\"")) {
            return sinEspacios;
        }
        return sinEspacios.replaceAll("\"\\s*\"", "").replace("\"", "");
    }
}
