package com.facimus.procesos.common.almacen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * PR 61: el adaptador S3 contra un servidor S3 de verdad, el SeaweedFS de compose.yaml. Lo que importa del ancla se
 * prueba aqui: una version guardada con Object Lock en modo compliance no se puede borrar, ni con las credenciales de
 * la API. Donde no hay contenedores de Linux (los runners de Windows, que apuntan a un PostgreSQL de afuera con
 * PRUEBAS_POSTGRES_URL) no corre.
 */
@DisabledIfEnvironmentVariable(named = "PRUEBAS_POSTGRES_URL", matches = ".+",
        disabledReason = "sin contenedores de Linux, como en los runners de Windows")
class AlmacenS3IntegracionTest {

    /** La misma imagen que compose.yaml, fijada por su huella. */
    static final String IMAGEN = "chrislusf/seaweedfs:4.50@sha256:"
            + "c2658820e763294a9e9de7b36c81e59b879e3459148351aee4e31a126ca64860";
    private static final String USUARIO = "prueba";
    private static final String CLAVE = "prueba-secreta";

    private static GenericContainer<?> seaweed;
    private static S3Client s3;

    @TempDir
    Path carpeta;

    @BeforeAll
    static void arrancar() {
        seaweed = new GenericContainer<>(DockerImageName.parse(IMAGEN))
                .withCommand("server", "-s3", "-dir=/data", "-master.volumeSizeLimitMB=32", "-volume.max=100")
                .withEnv("AWS_ACCESS_KEY_ID", USUARIO).withEnv("AWS_SECRET_ACCESS_KEY", CLAVE)
                .withExposedPorts(8333)
                .waitingFor(Wait.forHttp("/").forPort(8333).forStatusCodeMatching(codigo -> codigo == 403)
                        .withStartupTimeout(Duration.ofMinutes(2)));
        seaweed.start();
        s3 = S3Client.builder().region(Region.US_EAST_1).httpClient(UrlConnectionHttpClient.create())
                .endpointOverride(URI.create("http://" + seaweed.getHost() + ":" + seaweed.getMappedPort(8333)))
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(USUARIO, CLAVE)))
                .build();
        s3.createBucket(peticion -> peticion.bucket("documentos"));
        s3.createBucket(peticion -> peticion.bucket("anclas").objectLockEnabledForBucket(true));
    }

    @AfterAll
    static void apagar() {
        if (s3 != null) {
            s3.close();
        }
        if (seaweed != null) {
            seaweed.stop();
        }
    }

    private Path archivo(String contenido) throws IOException {
        return Files.writeString(carpeta.resolve("subida-" + System.nanoTime()), contenido);
    }

    private static String leer(Almacen almacen, String clave) throws IOException {
        try (InputStream contenido = almacen.abrir(clave)) {
            return new String(contenido.readAllBytes());
        }
    }

    @Test
    @DisplayName("guarda, abre, reemplaza y borra por prefijo sin tocar lo de otra tienda")
    void documentos() throws IOException {
        Almacen almacen = new AlmacenS3(s3, "documentos", "anclas", false);
        String deLaUno = ClaveDeAlmacen.deDocumento(1, 12, 1, "a");
        String deLaOnce = ClaveDeAlmacen.deDocumento(11, 13, 1, "b");

        almacen.guardar(deLaUno, archivo("primera"), "text/plain");
        almacen.guardar(deLaUno, archivo("segunda"), "text/plain");
        almacen.guardar(deLaOnce, archivo("de otra"), "text/plain");

        assertThat(leer(almacen, deLaUno)).isEqualTo("segunda");
        almacen.borrarPrefijo(ClaveDeAlmacen.deEmpresa(1));
        assertThatThrownBy(() -> almacen.abrir(deLaUno)).isInstanceOf(ArchivoNoEncontradoException.class);
        assertThat(leer(almacen, deLaOnce)).isEqualTo("de otra");
        assertThat(almacen.descripcion()).isEqualTo("S3, bucket documentos, anclas en anclas");
    }

    @Test
    @DisplayName("borrar un prefijo con mas de mil archivos recorre todas las paginas")
    void muchas() throws IOException {
        Almacen almacen = new AlmacenS3(s3, "documentos", "", false);
        Path uno = archivo("x");
        for (int i = 0; i < 1005; i++) {
            almacen.guardar(ClaveDeAlmacen.deDocumento(2, i, 1, "a"), uno, "text/plain");
        }

        almacen.borrarPrefijo(ClaveDeAlmacen.deEmpresa(2));

        assertThat(s3.listObjectsV2(peticion -> peticion.bucket("documentos").prefix("empresas/2/")).contents())
                .isEmpty();
    }

    @Test
    @DisplayName("un ancla con Object Lock no se puede borrar antes de su fecha, ni con las credenciales de la API")
    void anclaInmutable() {
        Almacen almacen = new AlmacenS3(s3, "documentos", "anclas", false);
        String clave = ClaveDeAlmacen.deAncla(3, "2026-11-12");

        boolean bloqueado = almacen.guardarInmutable(clave, "{\"hash\":\"abc\"}".getBytes(),
                Instant.now().plus(Duration.ofDays(1)));

        assertThat(bloqueado).isTrue();
        List<ObjectVersion> versiones = s3.listObjectVersions(peticion -> peticion.bucket("anclas").prefix(clave))
                .versions();
        assertThat(versiones).hasSize(1);
        String version = versiones.getFirst().versionId();
        assertThatThrownBy(() -> s3.deleteObject(peticion -> peticion.bucket("anclas").key(clave).versionId(version)))
                .isInstanceOf(S3Exception.class).satisfies(error -> assertThat(((S3Exception) error).statusCode())
                        .isEqualTo(403));
        assertThat(new String(s3.getObjectAsBytes(peticion -> peticion.bucket("anclas").key(clave)
                .versionId(version)).asByteArray())).isEqualTo("{\"hash\":\"abc\"}");
    }

    @Test
    @DisplayName("crea los buckets que faltan, el de anclas con Object Lock; si ya estan, no hace nada")
    void crearBuckets() {
        AlmacenS3 almacen = new AlmacenS3(s3, "nuevos-documentos", "nuevas-anclas", false);

        almacen.prepararBuckets(35);
        almacen.prepararBuckets(35);

        assertThat(s3.listBuckets().buckets()).extracting(Bucket::name)
                .contains("nuevos-documentos", "nuevas-anclas");
        assertThat(s3.getObjectLockConfiguration(peticion -> peticion.bucket("nuevas-anclas"))
                .objectLockConfiguration().objectLockEnabledAsString()).isEqualTo("Enabled");
    }

    @Test
    @DisplayName("PR 67: el bucket de documentos, nuevo o de antes, guarda cada version y vence las viejas")
    void versiones() {
        s3.createBucket(peticion -> peticion.bucket("documentos-de-antes"));
        AlmacenS3 almacen = new AlmacenS3(s3, "documentos-de-antes", "", false);

        almacen.prepararBuckets(35);

        assertThat(s3.getBucketVersioning(peticion -> peticion.bucket("documentos-de-antes")).status())
                .isEqualTo(BucketVersioningStatus.ENABLED);
        assertThat(s3.getBucketLifecycleConfiguration(peticion -> peticion.bucket("documentos-de-antes")).rules())
                .singleElement().satisfies(regla -> {
                    assertThat(regla.status()).isEqualTo(ExpirationStatus.ENABLED);
                    assertThat(regla.noncurrentVersionExpiration().noncurrentDays()).isEqualTo(35);
                });
    }

    @Test
    @DisplayName("PR 67: un documento reemplazado o borrado con su tienda se recupera de su version anterior")
    void recuperarDeUnaVersion() throws IOException {
        AlmacenS3 almacen = new AlmacenS3(s3, "documentos-con-versiones", "", false);
        almacen.prepararBuckets(35);
        String clave = ClaveDeAlmacen.deDocumento(5, 14, 1, "a");
        almacen.guardar(clave, archivo("primera"), "text/plain");
        almacen.guardar(clave, archivo("segunda"), "text/plain");

        almacen.borrarPrefijo(ClaveDeAlmacen.deEmpresa(5));

        assertThatThrownBy(() -> almacen.abrir(clave)).isInstanceOf(ArchivoNoEncontradoException.class);
        ListObjectVersionsResponse historia = s3.listObjectVersions(peticion -> peticion
                .bucket("documentos-con-versiones").prefix(clave));
        assertThat(historia.deleteMarkers()).hasSize(1);
        assertThat(historia.versions()).hasSize(2).extracting(version -> new String(s3.getObjectAsBytes(peticion ->
                peticion.bucket("documentos-con-versiones").key(clave).versionId(version.versionId())).asByteArray()))
                .containsExactlyInAnyOrder("primera", "segunda");
    }

    @Test
    @DisplayName("sin bucket de anclas, el ancla va al de documentos y el almacen dice que no la puede bloquear")
    void sinBucketDeAnclas() throws IOException {
        Almacen almacen = new AlmacenS3(s3, "documentos", "", false);
        String clave = ClaveDeAlmacen.deAncla(4, "2026-11-12");

        boolean bloqueado = almacen.guardarInmutable(clave, "{}".getBytes(), Instant.now().plusSeconds(60));

        assertThat(bloqueado).isFalse();
        assertThat(leer(almacen, clave)).isEqualTo("{}");
        assertThat(almacen.descripcion()).isEqualTo("S3, bucket documentos");
    }
}
