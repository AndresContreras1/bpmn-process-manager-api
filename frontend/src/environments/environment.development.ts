// Desarrollo: URL relativa. ng serve reenvia /api al backend local (proxy.conf.json), asi que el navegador no
// hace peticiones a otro origen y el frontend funciona en cualquier puerto sin tocar el CORS del backend.
export const environment = {
  apiUrl: '',
};
