/** Las de siempre, para el navegador que no sabe listar las suyas. */
const CONOCIDAS: readonly string[] = [
  'America/Bogota',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'America/Mexico_City',
  'America/Lima',
  'America/Sao_Paulo',
  'Europe/London',
  'Europe/Madrid',
  'Europe/Paris',
  'Australia/Sydney',
  'Australia/Perth',
  'Asia/Tokyo',
];

/**
 * Las zonas horarias que se pueden elegir: las que el navegador conoce de la base de IANA, que es la misma que valida
 * la API, mas UTC y las que ya estan guardadas, para que una elegida en otro navegador no desaparezca de la lista.
 */
export function zonasHorarias(...guardadas: (string | null | undefined)[]): string[] {
  const intl = Intl as typeof Intl & { supportedValuesOf?: (clave: string) => string[] };
  const delNavegador: string[] = typeof intl.supportedValuesOf === 'function' ? intl.supportedValuesOf('timeZone') : [];
  const todas = new Set<string>([...(delNavegador.length > 0 ? delNavegador : CONOCIDAS), 'UTC']);
  guardadas.forEach((zona) => zona && todas.add(zona));
  return [...todas].sort((a: string, b: string) => a.localeCompare(b));
}
