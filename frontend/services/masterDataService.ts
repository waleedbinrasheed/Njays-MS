import { api } from "@/services/apiClient";
import type { Page, PageQuery } from "@/types/api";
import type {
  Category,
  CategoryInput,
  Customer,
  CustomerInput,
  Party,
  PartyInput,
  Product,
  ProductInput,
  Representative,
  RepresentativeInput,
  RepresentativeRef,
} from "@/types/master";

export interface ListQuery extends PageQuery {
  search?: string;
  active?: boolean;
  [filter: string]: string | number | boolean | undefined;
}

export interface CrudService<T, TInput> {
  list: (query: ListQuery, signal?: AbortSignal) => Promise<Page<T>>;
  get: (id: number) => Promise<T>;
  create: (input: TInput) => Promise<T>;
  update: (id: number, input: TInput) => Promise<T>;
  remove: (id: number) => Promise<void>;
}

function crud<T, TInput>(path: string): CrudService<T, TInput> {
  return {
    list: (query, signal) => api.get<Page<T>>(path, query, signal),
    get: (id) => api.get<T>(`${path}/${id}`),
    create: (input) => api.post<T>(path, input),
    update: (id, input) => api.put<T>(`${path}/${id}`, input),
    remove: (id) => api.delete<void>(`${path}/${id}`),
  };
}

export const productService = crud<Product, ProductInput>("/products");
export const publisherService = crud<Party, PartyInput>("/publishers");
export const supplierService = crud<Party, PartyInput>("/suppliers");
export const categoryService = crud<Category, CategoryInput>("/categories");
export const customerService = crud<Customer, CustomerInput>("/customers");

export const representativeService = {
  ...crud<Representative, RepresentativeInput>("/representatives"),
  /** The built-in "Counter Sales" representative, preselected for counter sales. */
  counter: (signal?: AbortSignal) => api.get<Representative>("/representatives/counter", undefined, signal),
  moveCustomers: (fromId: number, targetRepresentativeId: number) =>
    api.post<{ moved: number; from: RepresentativeRef; to: RepresentativeRef }>(`/representatives/${fromId}/move-customers`, {
      targetRepresentativeId,
    }),
};
