/** Error body returned by every backend failure (see backend ApiError). */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  message: string;
  path: string;
  errors?: { field: string; message: string }[];
}

/** Paginated list shape returned by the backend (page is zero-based). */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PageQuery {
  page?: number;
  size?: number;
  /** e.g. "username,asc" */
  sort?: string;
}
