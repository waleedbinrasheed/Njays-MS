import type { Option } from "@/components/ui/AsyncSelect";
import { categoryService, publisherService, supplierService } from "@/services/masterDataService";

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

export async function loadCategoryOptions(search: string, signal: AbortSignal): Promise<Option[]> {
  const page = await categoryService.list({ search, active: true, size: LOOKUP_SIZE, sort: "name,asc" }, signal);
  return page.content.map((c) => ({ id: c.id, label: c.name }));
}
