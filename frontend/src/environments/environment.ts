// Produccion: URL relativa. La aplicacion se sirve detras de NGINX, que reenvia lo que empieza por /api al
// contenedor de la API, asi que el navegador pide todo al mismo origen: ni CORS que configurar, ni una URL
// distinta por entorno, ni que recompilar para cambiar de maquina.
export const environment = {
  apiUrl: '',
  // La tienda de demostracion la siembra el perfil dev de la API, y el stack de contenedores corre prod: sin esto
  // la portada y el login ofrecerian una cuenta que ahi no existe. Ponlo en true si despliegas un stack con dev.
  demo: false,
};
