package com.facimus.procesos.e2e;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Los datos que necesita una prueba se crean por la API, no pulsando botones. Pulsar botones para preparar el
 * escenario haria que una prueba de publicar fallase porque se rompio el alta de usuarios, y entonces el fallo no
 * diria nada.
 *
 * El cliente HTTP viene con Java, asi que aqui no hay framework ninguno: solo lo justo para hablar con la API.
 */
class ApiDeDatos {

    private final HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String api;
    private String token;
    private long usuarioId;

    ApiDeDatos(String api) {
        this.api = api;
    }

    /** Registra una tienda con su administrador y deja la sesion abierta para lo que venga despues. */
    Tienda registrarTienda(String nombre, String nit, String correo, String clave) {
        JsonNode empresa = post("/empresas", """
                {"nombreEmpresa":"%s","nit":"%s","correoContacto":"%s",
                 "nombreAdmin":"%s","emailAdmin":"%s","passwordAdmin":"%s"}
                """.formatted(nombre, nit, correo, nombre + " Admin", correo, clave));
        entrar(correo, clave);
        return new Tienda(empresa.get("id").asLong(), nit, correo, clave);
    }

    void entrar(String correo, String clave) {
        JsonNode sesion = post("/auth/login", """
                {"email":"%s","password":"%s"}
                """.formatted(correo, clave));
        token = sesion.get("accessToken").asText();
        usuarioId = sesion.get("usuario").get("id").asLong();
    }

    /** Quien esta dentro con esta sesion: hace falta para darle roles de proceso y que tenga bandeja. */
    long usuarioId() {
        return usuarioId;
    }

    /** Publica el proceso sobre la version que tenga ahora, que es lo que la API compara. */
    void publicar(long procesoId) {
        long version = get("/procesos/" + procesoId).get("proceso").get("version").asLong();
        patch("/procesos/" + procesoId, """
                {"estado":"PUBLICADO","version":%d}
                """.formatted(version));
    }

    /** Los roles de proceso que atiende alguien: de ahi sale su bandeja. */
    void rolesDeUsuario(long usuarioId, long rolId) {
        put("/usuarios/" + usuarioId + "/roles-proceso", """
                {"rolesProcesoIds":[%d]}
                """.formatted(rolId));
    }

    long crearProceso(String nombre, String descripcion, String categoria) {
        return post("/procesos", """
                {"nombre":"%s","descripcion":"%s","categoria":"%s"}
                """.formatted(nombre, descripcion, categoria)).get("id").asLong();
    }

    /**
     * Da de alta a alguien con su propia clave. Con una clave puesta no hay temporal, y por lo tanto tampoco el
     * cambio obligatorio: la prueba puede entrar con ella y trabajar.
     */
    void crearUsuario(String nombre, String correo, String clave, String rol) {
        post("/usuarios", """
                {"nombre":"%s","email":"%s","password":"%s","rolAcceso":"%s"}
                """.formatted(nombre, correo, clave, rol));
    }

    long crearRol(String nombre) {
        return post("/roles", """
                {"nombre":"%s","descripcion":"Creado por una prueba de punta a punta"}
                """.formatted(nombre)).get("id").asLong();
    }

    /** El pool de la tienda lo crea la API con el proceso, asi que se lee en vez de crearse. */
    long poolDeLaTienda(long procesoId) {
        return get("/procesos/" + procesoId + "/pools").get(0).get("id").asLong();
    }

    long crearLane(long poolId, String nombre, long rolId) {
        return post("/pools/" + poolId + "/lanes", """
                {"nombre":"%s","rolProcesoId":%d}
                """.formatted(nombre, rolId)).get("id").asLong();
    }

    long crearEvento(long laneId, String nombre, String tipo, int x, int y) {
        return post("/lanes/" + laneId + "/eventos", """
                {"nombre":"%s","tipoEvento":"%s","posicionX":%d,"posicionY":%d}
                """.formatted(nombre, tipo, x, y)).get("id").asLong();
    }

    long crearActividad(long laneId, String nombre, String tipo, int x, int y) {
        return post("/lanes/" + laneId + "/actividades", """
                {"nombre":"%s","descripcion":"Creada por una prueba","tipoActividad":"%s",
                 "posicionX":%d,"posicionY":%d}
                """.formatted(nombre, tipo, x, y)).get("id").asLong();
    }

    void conectar(long origenId, long destinoId) {
        post("/arcos", """
                {"origenId":%d,"destinoId":%d}
                """.formatted(origenId, destinoId));
    }

    JsonNode get(String ruta) {
        return enviar(peticion(ruta).GET());
    }

    JsonNode post(String ruta, String cuerpo) {
        return enviar(peticion(ruta).POST(HttpRequest.BodyPublishers.ofString(cuerpo)));
    }

    JsonNode patch(String ruta, String cuerpo) {
        return enviar(peticion(ruta).method("PATCH", HttpRequest.BodyPublishers.ofString(cuerpo)));
    }

    JsonNode put(String ruta, String cuerpo) {
        return enviar(peticion(ruta).PUT(HttpRequest.BodyPublishers.ofString(cuerpo)));
    }

    private HttpRequest.Builder peticion(String ruta) {
        HttpRequest.Builder constructor = HttpRequest.newBuilder(URI.create(api + ruta))
                .header("Content-Type", "application/json");
        return token == null ? constructor : constructor.header("Authorization", "Bearer " + token);
    }

    private JsonNode enviar(HttpRequest.Builder constructor) {
        try {
            HttpResponse<String> respuesta = cliente.send(constructor.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() >= 400) {
                throw new IllegalStateException(
                        "La API respondio " + respuesta.statusCode() + ": " + respuesta.body());
            }
            return respuesta.body().isBlank() ? json.createObjectNode() : json.readTree(respuesta.body());
        } catch (java.io.IOException error) {
            throw new IllegalStateException("No se pudo hablar con la API en " + api, error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpida la llamada a la API", error);
        }
    }

    /** Una tienda recien creada y las claves con las que entrar a ella desde el navegador. */
    record Tienda(long id, String nit, String correo, String clave) {
    }
}
