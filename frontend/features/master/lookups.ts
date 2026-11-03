import type { Option, OptionLoader, OptionPage } from "@/components/ui/AsyncSelect";
import { customerService, fabricService, garmentTypeService } from "@/services/masterDataService";
import type { Page } from "@/types/api";
import { formatCurrency } from "@/utils/format";

/** Options per page in pickers; further pages load as the user scrolls. */
export const LOOKUP_SIZE = 20;

/** One page of API results as picker options. */
export function toOptionPage<T>(page: Page<T>, toOption: (row: T) => Option | null): OptionPage {
  return {
    options: page.content.map(toOption).filter((o): o is Option => o !== null),
    hasMore: page.page + 1 < page.totalPages,
  };
}

/** Active customers matching the typed text (name, phone or email). */
export const loadCustomerOptions: OptionLoader = async (search, signal, page) =>
  toOptionPage(await customerService.list({ search, active: true, page, size: LOOKUP_SIZE, sort: "name,asc" }, signal), (c) => ({
    id: c.id,
    label: c.name,
    detail: [c.phone, c.address].filter(Boolean).join(" · ") || undefined,
  }));

/** Active garment types. */
export const loadGarmentTypeOptions: OptionLoader = async (search, signal, page) =>
  toOptionPage(await garmentTypeService.list({ search, active: true, page, size: LOOKUP_SIZE, sort: "name,asc" }, signal), (g) => ({
    id: g.id,
    label: g.name,
    data: g,
  }));

/** Active fabrics, showing the quantity on hand and the price per metre. */
export const loadFabricOptions: OptionLoader = async (search, signal, page) =>
  toOptionPage(await fabricService.list({ search, active: true, page, size: LOOKUP_SIZE, sort: "code,asc" }, signal), (f) => ({
    id: f.id,
    label: `${f.code} · ${f.name}${f.colour ? ` · ${f.colour}` : ""}`,
    detail: `On hand: ${f.quantityOnHand} m · ${formatCurrency(f.pricePerMetre)} per m`,
    data: f,
  }));
