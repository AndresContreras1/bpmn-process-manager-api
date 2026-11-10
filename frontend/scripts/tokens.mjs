// Convierte los tokens de diseno (src/design/tokens.json, formato DTCG 2025.10) en lo que usa la web:
// src/styles/_tokens.scss, con las variables de Sass que Bootstrap lee antes de compilarse y las variables CSS que usan
// los estilos propios, en claro y en oscuro. Corre antes de compilar, de servir y de probar (npm run tokens); el archivo
// que escribe no se guarda en el repositorio, asi que nunca se separa de los tokens.
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const tokens = JSON.parse(readFileSync(resolve(raiz, 'src/design/tokens.json'), 'utf8'));

/** Cada token por su ruta con puntos: color.verde.600. */
function aplanar(grupo, prefijo = [], salida = new Map()) {
  for (const [clave, valor] of Object.entries(grupo)) {
    if (clave.startsWith('$')) {
      continue;
    }
    if (valor && typeof valor === 'object' && '$value' in valor) {
      salida.set([...prefijo, clave].join('.'), valor);
    } else if (valor && typeof valor === 'object') {
      aplanar(valor, [...prefijo, clave], salida);
    }
  }
  return salida;
}

const planos = aplanar(tokens);

/** El valor de un token, siguiendo los alias ({color.verde.600}) hasta uno concreto. */
function resolver(ruta, vistos = new Set()) {
  if (vistos.has(ruta)) {
    throw new Error(`Los alias de ${ruta} se llaman en circulo.`);
  }
  vistos.add(ruta);
  const token = planos.get(ruta);
  if (!token) {
    throw new Error(`No hay un token ${ruta}.`);
  }
  const valor = token.$value;
  const alias = typeof valor === 'string' && /^\{(.+)\}$/.exec(valor);
  return alias ? resolver(alias[1], vistos) : { tipo: token.$type, valor };
}

/** Como se escribe en CSS cada tipo de token del formato. */
function css({ tipo, valor }) {
  switch (tipo) {
    case 'color':
      return valor.hex;
    case 'dimension':
      return `${valor.value}${valor.unit}`;
    case 'fontFamily':
      return valor.map((familia) => (/\s/.test(familia) ? `"${familia}"` : familia)).join(', ');
    default:
      throw new Error(`El tipo de token ${tipo} no tiene forma en CSS.`);
  }
}

const nombre = (ruta) => ruta.replaceAll('.', '-');
const valorDe = (ruta) => css(resolver(ruta));

/** Lo que Bootstrap lee de los tokens: su color primario, los de estado, el fondo, los radios y el modo oscuro. */
const bootstrap = {
  primary: 'color.marca.primario',
  secondary: 'color.marca.secundario',
  success: 'color.estado.exito',
  warning: 'color.estado.aviso',
  danger: 'color.estado.peligro',
  info: 'color.estado.info',
  'body-bg': 'color.fondo.pagina',
  'body-color': 'color.fondo.texto',
  'body-secondary-color': 'color.fondo.texto-secundario',
  'link-color': 'color.marca.enlace',
  'border-radius': 'radio.base',
  'border-radius-lg': 'radio.grande',
  'font-family-sans-serif': 'fuente.familia',
  'body-bg-dark': 'color.oscuro.fondo',
  'body-tertiary-bg-dark': 'color.oscuro.superficie',
  'body-color-dark': 'color.oscuro.texto',
  'body-secondary-color-dark': 'color.oscuro.texto-secundario',
  'border-color-dark': 'color.oscuro.borde',
  'link-color-dark': 'color.marca.enlace-oscuro',
  'link-hover-color-dark': 'color.verde.200',
};

const lineas = [
  '// Generado por scripts/tokens.mjs desde src/design/tokens.json: no se edita a mano.',
  '',
  '// Los tokens, como variables de Sass.',
  ...[...planos.keys()].map((ruta) => `$bpm-${nombre(ruta)}: ${valorDe(ruta)};`),
  '',
  '// Lo que Bootstrap lee antes de compilarse.',
  ...Object.entries(bootstrap).map(([variable, ruta]) => `$${variable}: $bpm-${nombre(ruta)};`),
  '',
  '// Los tokens, como variables CSS para los estilos propios.',
  ':root {',
  ...[...planos.keys()]
    .filter((ruta) => !ruta.startsWith('fuente.'))
    .map((ruta) => `  --bpm-${nombre(ruta)}: ${valorDe(ruta)};`),
  '}',
  '',
];

const destino = resolve(raiz, 'src/styles/_tokens.scss');
mkdirSync(dirname(destino), { recursive: true });
writeFileSync(destino, lineas.join('\n'));
console.log(`Tokens: ${planos.size} en ${destino}`);
