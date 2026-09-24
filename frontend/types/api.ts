/** Error body returned by every backend failure (see backend ApiError). */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  message: string;
  path: string;
  errors?: { field: string; message: string }[];
}

/** Spring Data page shape used by all paginated list endpoints. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
