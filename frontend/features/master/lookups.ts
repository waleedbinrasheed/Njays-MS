import type { Option, OptionLoader, OptionPage } from "@/components/ui/AsyncSelect";
import {
  categoryService,
  customerService,
  publisherService,
  representativeService,
  supplierService,
} from "@/services/masterDataService";
import type { Page } from "@/types/api";

/** Options per page in pickers; further pages load as the user scrolls. */
export const LOOKUP_SIZE = 20;

/** One page of API results as picker options. */
export function toOptionPage<T>(page: Page<T>, toOption: (row: T) => Option | null): OptionPage {
  return {
    options: page.content.map(toOption).filter((o): o is Option => o !== null),
    hasMore: page.page + 1 < page.totalPages,
  };
}

/** Active publishers matching the typed text, for pickers. */
export const loadPublisherOptions: OptionLoader = async (search, signal, page) =>
  toOptionPage(await publisherService.list({ search, active: true, page, size: LOOKUP_SIZE, sort: "name,asc" }, signal),
    (p) => ({ id: p.id, label: p.name }));

export const loadSupplierOptions: OptionLoader = async (search, signal, page) =>
  toOptionPage(await supplierService.list({ search, active: true, page, size: LOOKUP_SIZE, sort: "name,asc" }, signal),
    (s) => ({ id: s.id, label: s.name, detail: s.phone ?? undefined }));

export const loadCustomerOptions: OptionLoader = async (search, signal, page) =>
  toOptionPage(await customerService.list({ search, active: true, page, size: LOOKUP_SIZE, sort: "name,asc" }, signal),
    (c) => ({
      id: c.id,
      label: c.name,
      detail: [c.phone, c.representative ? `Rep: ${c.representative.name}` : null].filter(Boolean).join(" · ") || undefined,
    }));

/** Active representatives (for assigning customers and for selling). */
export const loadRepresentativeOptions: OptionLoader = async (search, signal, page) =>
  toOptionPage(await representativeService.list({ search, active: true, page, size: LOOKUP_SIZE, sort: "name,asc" }, signal),
    (r) => ({ id: r.id, label: r.name, detail: [r.area, r.phone].filter(Boolean).join(" · ") || undefined }));

/** Active representatives who can take samples (not the built-in Counter Sales one). */
export const loadSampleRepresentativeOptions: OptionLoader = async (search, signal, page) =>
  toOptionPage(await representativeService.list({ search, active: true, page, size: LOOKUP_SIZE, sort: "name,asc" }, signal),
    (r) => (r.builtIn ? null : { id: r.id, label: r.name, detail: [r.area, r.phone].filter(Boolean).join(" · ") || undefined }));

/** All representatives including inactive ones (for filters and reports). */
export const loadAnyRepresentativeOptions: OptionLoader = async (search, signal, page) =>
  toOptionPage(await representativeService.list({ search, page, size: LOOKUP_SIZE, sort: "name,asc" }, signal),
    (r) => ({ id: r.id, label: r.name, detail: [r.area, r.active ? null : "Inactive"].filter(Boolean).join(" · ") || undefined }));

/** Active customers belonging to one representative. */
export function representativeCustomerLoader(representativeId: number): OptionLoader {
  return async (search, signal, page) =>
    toOptionPage(
      await customerService.list({ search, active: true, representativeId, page, size: LOOKUP_SIZE, sort: "name,asc" }, signal),
      (c) => ({ id: c.id, label: c.name, detail: c.phone ?? undefined }),
    );
}

export const loadCategoryOptions: OptionLoader = async (search, signal, page) =>
  toOptionPage(await categoryService.list({ search, active: true, page, size: LOOKUP_SIZE, sort: "name,asc" }, signal),
    (c) => ({ id: c.id, label: c.name }));
