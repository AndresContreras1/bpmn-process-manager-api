/**
 * El contraste entre dos colores, como lo mide WCAG 2.2: la razon entre sus luminancias relativas, de 1 a 21. El texto
 * normal pide 4,5 para el nivel AA y el texto grande o los bordes de un control, 3.
 */
export const AA_TEXTO: number = 4.5;
export const AA_CONTROL: number = 3;

/** La luminancia relativa de un color #rrggbb. */
export function luminancia(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((inicio: number) => {
    const canal: number = parseInt(hex.slice(inicio, inicio + 2), 16) / 255;
    return canal <= 0.04045 ? canal / 12.92 : ((canal + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function contraste(uno: string, otro: string): number {
  const [claro, oscuro] = [luminancia(uno), luminancia(otro)].sort((a: number, b: number) => b - a);
  return (claro + 0.05) / (oscuro + 0.05);
}

interface TokenDeColor {
  $value: { hex: string } | string;
}

/** El color de un token por su ruta, siguiendo los alias como {color.verde.600}. */
export function colorDe(tokens: object, ruta: string): string {
  const token: TokenDeColor | undefined = ruta
    .split('.')
    .reduce<unknown>(
      (grupo: unknown, clave: string) => (grupo as Record<string, unknown> | undefined)?.[clave],
      tokens,
    ) as TokenDeColor | undefined;
  if (!token) {
    throw new Error(`No hay un token ${ruta}.`);
  }
  const valor = token.$value;
  return typeof valor === 'string' ? colorDe(tokens, valor.slice(1, -1)) : valor.hex;
}
