import { api } from "@/services/apiClient";
import type { Page, PageQuery } from "@/types/api";
import type { CreateReturnInput, ReturnDetail, ReturnKind, ReturnSummary, ReturnableLine } from "@/types/returns";

export interface ReturnQuery extends PageQuery {
  search?: string;
  documentId?: number;
  from?: string;
  to?: string;
}

function returnsApi(kind: ReturnKind) {
  const path = kind === "sale" ? "/sale-returns" : "/purchase-returns";
  const docParam = kind === "sale" ? "saleId" : "purchaseId";
  return {
    list: ({ documentId, ...rest }: ReturnQuery, signal?: AbortSignal) =>
      api.get<Page<ReturnSummary>>(path, { ...rest, [docParam]: documentId }, signal),
    get: (id: number, signal?: AbortSignal) => api.get<ReturnDetail>(`${path}/${id}`, undefined, signal),
    returnable: (documentId: number, signal?: AbortSignal) =>
      api.get<ReturnableLine[]>(`${path}/returnable/${documentId}`, undefined, signal),
    create: (input: CreateReturnInput) => api.post<ReturnDetail>(path, input),
  };
}

export const returnServices = {
  sale: returnsApi("sale"),
  purchase: returnsApi("purchase"),
};
