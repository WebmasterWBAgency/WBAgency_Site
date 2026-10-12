/**
 * Extração defensiva de listas em respostas do backend (Spring Boot Page, ApiResponse<T>, ou arrays puros).
 * Garante que componentes recebam o array de dados reais sem telas em branco.
 */
export function extractList<T = any>(res: any): T[] {
  if (!res) return [];
  if (Array.isArray(res)) return res;
  if (res.content && Array.isArray(res.content)) return res.content;
  if (res.data) {
    if (Array.isArray(res.data)) return res.data;
    if (res.data.content && Array.isArray(res.data.content)) return res.data.content;
    if (res.data.items && Array.isArray(res.data.items)) return res.data.items;
  }
  if (res.items && Array.isArray(res.items)) return res.items;
  if (res.records && Array.isArray(res.records)) return res.records;
  return [];
}
