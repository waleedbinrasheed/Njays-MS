import { api } from "@/services/apiClient";
import type { Page, PageQuery } from "@/types/api";
import type { ReportPage } from "@/types/report";
import type {
  CreateSampleInput,
  CreateSampleReturnInput,
  SampleDetail,
  SampleLineRow,
  SampleReportSummary,
  SampleRepresentativeRow,
  SampleReturnDetail,
  SampleReturnSummary,
  SampleStatus,
  SampleSummary,
} from "@/types/sample";

export interface SampleQuery extends PageQuery {
  search?: string;
  representativeId?: number;
  customerId?: number;
  productId?: number;
  from?: string;
  to?: string;
  status?: SampleStatus;
}

export interface SampleReturnQuery extends PageQuery {
  search?: string;
  sampleId?: number;
  representativeId?: number;
  from?: string;
  to?: string;
}

type ReportQuery = PageQuery & Record<string, string | number | boolean | undefined>;

export const sampleService = {
  list: (query: SampleQuery, signal?: AbortSignal) => api.get<Page<SampleSummary>>("/samples", { ...query }, signal),
  get: (id: number, signal?: AbortSignal) => api.get<SampleDetail>(`/samples/${id}`, undefined, signal),
  create: (input: CreateSampleInput) => api.post<SampleDetail>("/samples", input),
  cancel: (id: number, reason: string) => api.post<SampleDetail>(`/samples/${id}/cancel`, { reason }),

  listReturns: (query: SampleReturnQuery, signal?: AbortSignal) =>
    api.get<Page<SampleReturnSummary>>("/sample-returns", { ...query }, signal),
  getReturn: (id: number, signal?: AbortSignal) => api.get<SampleReturnDetail>(`/sample-returns/${id}`, undefined, signal),
  createReturn: (input: CreateSampleReturnInput) => api.post<SampleReturnDetail>("/sample-returns", input),

  reportLines: (q: ReportQuery, signal?: AbortSignal) =>
    api.get<ReportPage<SampleLineRow, SampleReportSummary>>("/sample-reports/lines", q, signal),
  reportRepresentatives: (q: ReportQuery, signal?: AbortSignal) =>
    api.get<ReportPage<SampleRepresentativeRow, SampleReportSummary>>("/sample-reports/representatives", q, signal),
};
