/** Los idiomas de la web: cada uno es una compilacion, servida por NGINX en su carpeta (/es-CO/, /fr-FR/...). */
export type Idioma = 'es-CO' | 'en-US' | 'en-AU' | 'fr-FR';

/**
 * El idioma y la zona horaria de quien entro, como los devuelve la API. Los dos primeros son los que valen; los
 * propios son null mientras la persona siga los de su organizacion.
 */
export interface Preferencias {
  idioma: Idioma;
  zonaHoraria: string;
  idiomaPropio: Idioma | null;
  zonaHorariaPropia: string | null;
  idiomaDeLaOrganizacion: Idioma;
  zonaHorariaDeLaOrganizacion: string;
}

/** Lo que se manda al cambiarlas. Vacio, en las propias, vuelve a seguir lo de la organizacion. */
export interface PreferenciasRequest {
  idioma: Idioma | null;
  zonaHoraria: string | null;
}
