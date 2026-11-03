import { api } from "@/services/apiClient";
import type { Page, PageQuery } from "@/types/api";
import type {
  Customer,
  CustomerInput,
  Fabric,
  FabricInput,
  GarmentType,
  GarmentTypeInput,
  MeasurementRecord,
  MeasurementRecordInput,
} from "@/types/master";

export interface ListQuery extends PageQuery {
  search?: string;
  active?: boolean;
  [filter: string]: string | number | boolean | undefined;
}

export interface CrudService<T, TInput> {
  list: (query: ListQuery, signal?: AbortSignal) => Promise<Page<T>>;
  get: (id: number, signal?: AbortSignal) => Promise<T>;
  create: (input: TInput) => Promise<T>;
  update: (id: number, input: TInput) => Promise<T>;
  remove: (id: number) => Promise<void>;
}

function crud<T, TInput>(path: string): CrudService<T, TInput> {
  return {
    list: (query, signal) => api.get<Page<T>>(path, query, signal),
    get: (id, signal) => api.get<T>(`${path}/${id}`, undefined, signal),
    create: (input) => api.post<T>(path, input),
    update: (id, input) => api.put<T>(`${path}/${id}`, input),
    remove: (id) => api.delete<void>(`${path}/${id}`),
  };
}

export const customerService = crud<Customer, CustomerInput>("/customers");
export const fabricService = crud<Fabric, FabricInput>("/fabrics");
export const garmentTypeService = crud<GarmentType, GarmentTypeInput>("/garment-types");

export const measurementService = {
  /** Records of one customer, newest first (the first 100; the API's page limit). */
  forCustomer: (customerId: number, signal?: AbortSignal) =>
    api
      .get<Page<MeasurementRecord>>(`/customers/${customerId}/measurements`, { size: 100 }, signal)
      .then((page) => page.content),
  takeForCustomer: (customerId: number, input: MeasurementRecordInput) =>
    api.post<MeasurementRecord>(`/customers/${customerId}/measurements`, input),
  /** All records across customers (read-only). */
  list: (query: ListQuery, signal?: AbortSignal) => api.get<Page<MeasurementRecord>>("/measurements", query, signal),
  get: (id: number, signal?: AbortSignal) => api.get<MeasurementRecord>(`/measurements/${id}`, undefined, signal),
};
