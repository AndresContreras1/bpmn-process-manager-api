package com.facimus.procesos.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.almacen.AlmacenEnDisco;
import com.facimus.procesos.common.almacen.AlmacenS3;
import com.facimus.procesos.common.almacen.EnlacesFirmados;
import com.facimus.procesos.common.antivirus.Antivirus;
import com.facimus.procesos.common.antivirus.ClamAv;
import com.facimus.procesos.common.antivirus.SinAntivirus;
import com.facimus.procesos.common.archivos.DetectorDeTipo;
import com.facimus.procesos.common.archivos.InspeccionDeZip;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * PR 61: donde se guardan los documentos y quien los revisa. Con {@code ALMACEN_S3_BUCKET} van a ese bucket (de AWS,
 * o de cualquier servicio compatible con {@code ALMACEN_S3_ENDPOINT}); sin el, a una carpeta del disco, que sirve
 * para desarrollo y para una sola instancia. Con {@code ANTIVIRUS_HOST}, cada documento pasa por ClamAV antes de
 * poder bajarse; sin el, queda disponible con la marca de que nadie lo reviso, salvo que
 * {@code DOCUMENTOS_EXIGIR_ANTIVIRUS} diga que sin antivirus no se publica nada.
 */
@Configuration
public class AlmacenConfig {

    private static final Logger log = LoggerFactory.getLogger(AlmacenConfig.class);

    @Bean
    public Almacen almacen(@Value("${almacen.s3.bucket:}") String bucket,
            @Value("${almacen.s3.bucket-de-anclas:}") String bucketDeAnclas,
            @Value("${almacen.s3.endpoint:}") String endpoint, @Value("${almacen.s3.region:us-east-1}") String region,
            @Value("${almacen.s3.usuario:}") String usuario, @Value("${almacen.s3.clave:}") String clave,
            @Value("${almacen.s3.cifrar:false}") boolean cifrar,
            @Value("${almacen.s3.crear-buckets:false}") boolean crearBuckets,
            @Value("${almacen.directorio}") String directorio) {
        if (!StringUtils.hasText(bucket)) {
            AlmacenEnDisco disco = new AlmacenEnDisco(Path.of(directorio));
            log.info("Almacen de documentos: {} (sin ALMACEN_S3_BUCKET)", disco.descripcion());
            return disco;
        }
        S3ClientBuilder cliente = S3Client.builder().region(Region.of(region))
                .httpClient(UrlConnectionHttpClient.create());
        if (StringUtils.hasText(endpoint)) {
            // Un servicio compatible, como SeaweedFS: sus buckets van en la ruta, no en el nombre del host
            cliente.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        }
        cliente.credentialsProvider(StringUtils.hasText(usuario)
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(usuario, clave))
                : DefaultCredentialsProvider.builder().build());
        AlmacenS3 s3 = new AlmacenS3(cliente.build(), bucket, bucketDeAnclas, cifrar);
        if (crearBuckets) {
            s3.crearBucketsQueFalten();
        }
        log.info("Almacen de documentos: {}", s3.descripcion());
        return s3;
    }

    @Bean
    public Antivirus antivirus(@Value("${antivirus.host:}") String host, @Value("${antivirus.puerto:3310}") int puerto,
            @Value("${antivirus.espera:30s}") Duration espera) {
        if (!StringUtils.hasText(host)) {
            log.warn("Sin antivirus (ANTIVIRUS_HOST): los documentos no se revisan.");
            return new SinAntivirus();
        }
        return new ClamAv(host, puerto, espera);
    }

    @Bean
    public EnlacesFirmados enlacesFirmados(@Value("${jwt.secret:}") String secreto, Clock reloj) {
        return new EnlacesFirmados(secreto, reloj);
    }

    @Bean
    public DetectorDeTipo detectorDeTipo() {
        return new DetectorDeTipo();
    }

    @Bean
    public InspeccionDeZip inspeccionDeZip(@Value("${documentos.zip.maximo-descomprimido:200MB}") DataSize maximo,
            @Value("${documentos.zip.proporcion-maxima:100}") long proporcion,
            @Value("${documentos.zip.maximo-de-entradas:10000}") int entradas) {
        return new InspeccionDeZip(maximo.toBytes(), proporcion, entradas);
    }
}
