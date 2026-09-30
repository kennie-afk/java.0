/** Keyset page result. `next` is an opaque cursor for the following page, or null at the end. */
export interface Page<T, C> {
  items: T[];
  next: C | null;
}

/**
 * Reads at most `limit` rows from a cursor and reports whether more exist. Always asks for
 * one extra row so "is there a next page" is exact rather than a guess from a full page.
 */
export function collect<T>(
  source: IDBObjectStore | IDBIndex,
  range: IDBKeyRange | undefined,
  direction: IDBCursorDirection,
  limit: number
): Promise<{ rows: T[]; more: boolean }> {
  return new Promise((resolve, reject) => {
    const rows: T[] = [];
    const request = source.openCursor(range, direction);
    request.onerror = () => reject(request.error);
    request.onsuccess = () => {
      const cursor = request.result;
      if (!cursor) {
        resolve({ rows, more: false });
        return;
      }
      if (rows.length === limit) {
        resolve({ rows, more: true });
        return;
      }
      rows.push(cursor.value as T);
      cursor.continue();
    };
  });
}

export const clampLimit = (limit: number | undefined, fallback = 25): number =>
  Math.min(Math.max(Math.trunc(limit ?? fallback), 1), 100);
