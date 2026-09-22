/** Pagina de resultados, con los mismos campos que PageResponse de la API. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
