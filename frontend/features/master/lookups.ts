import type { Option } from "@/components/ui/AsyncSelect";
import {
  categoryService,
  customerService,
  publisherService,
  representativeService,
  supplierService,
} from "@/services/masterDataService";

const LOOKUP_SIZE = 20;

/** Active publishers matching the typed text, for pickers. */
export async function loadPublisherOptions(search: string, signal: AbortSignal): Promise<Option[]> {
  const page = await publisherService.list({ search, active: true, size: LOOKUP_SIZE, sort: "name,asc" }, signal);
  return page.content.map((p) => ({ id: p.id, label: p.name }));
}

export async function loadSupplierOptions(search: string, signal: AbortSignal): Promise<Option[]> {
  const page = await supplierService.list({ search, active: true, size: LOOKUP_SIZE, sort: "name,asc" }, signal);
  return page.content.map((s) => ({ id: s.id, label: s.name, detail: s.phone ?? undefined }));
}

export async function loadCustomerOptions(search: string, signal: AbortSignal): Promise<Option[]> {
  const page = await customerService.list({ search, active: true, size: LOOKUP_SIZE, sort: "name,asc" }, signal);
  return page.content.map((c) => ({
    id: c.id,
    label: c.name,
    detail: [c.phone, c.representative ? `Rep: ${c.representative.name}` : null].filter(Boolean).join(" · ") || undefined,
  }));
}

/** Active representatives (for assigning customers and for selling). */
export async function loadRepresentativeOptions(search: string, signal: AbortSignal): Promise<Option[]> {
  const page = await representativeService.list({ search, active: true, size: LOOKUP_SIZE, sort: "name,asc" }, signal);
  return page.content.map((r) => ({ id: r.id, label: r.name, detail: [r.area, r.phone].filter(Boolean).join(" · ") || undefined }));
}

/** Active representatives who can take samples (not the built-in Counter Sales one). */
export async function loadSampleRepresentativeOptions(search: string, signal: AbortSignal): Promise<Option[]> {
  const page = await representativeService.list({ search, active: true, size: LOOKUP_SIZE, sort: "name,asc" }, signal);
  return page.content
    .filter((r) => !r.builtIn)
    .map((r) => ({ id: r.id, label: r.name, detail: [r.area, r.phone].filter(Boolean).join(" · ") || undefined }));
}

/** All representatives including inactive ones (for filters and reports). */
export async function loadAnyRepresentativeOptions(search: string, signal: AbortSignal): Promise<Option[]> {
  const page = await representativeService.list({ search, size: LOOKUP_SIZE, sort: "name,asc" }, signal);
  return page.content.map((r) => ({ id: r.id, label: r.name, detail: [r.area, r.active ? null : "Inactive"].filter(Boolean).join(" · ") || undefined }));
}

/** Active customers belonging to one representative. */
export function representativeCustomerLoader(representativeId: number) {
  return async (search: string, signal: AbortSignal): Promise<Option[]> => {
    const page = await customerService.list(
      { search, active: true, representativeId, size: LOOKUP_SIZE, sort: "name,asc" },
      signal,
    );
    return page.content.map((c) => ({ id: c.id, label: c.name, detail: c.phone ?? undefined }));
  };
}

export async function loadCategoryOptions(search: string, signal: AbortSignal): Promise<Option[]> {
  const page = await categoryService.list({ search, active: true, size: LOOKUP_SIZE, sort: "name,asc" }, signal);
  return page.content.map((c) => ({ id: c.id, label: c.name }));
}
