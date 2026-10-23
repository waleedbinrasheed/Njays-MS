import type { OptionLoader } from "@/components/ui/AsyncSelect";
import { LOOKUP_SIZE, toOptionPage } from "@/features/master/lookups";
import { inventoryService } from "@/services/inventoryService";

/** Product picker that also shows current stock; searches ISBN, title and author. */
export const loadStockedProductOptions: OptionLoader = (search, signal, page) => loadProducts(search, signal, page, undefined);

/** Only products that can be bought or sold (active). */
export const loadActiveProductOptions: OptionLoader = (search, signal, page) => loadProducts(search, signal, page, true);

async function loadProducts(search: string, signal: AbortSignal, page: number, active: boolean | undefined) {
  return toOptionPage(await inventoryService.list({ search, active, page, size: LOOKUP_SIZE, sort: "title,asc" }, signal),
    (i) => ({
      id: i.productId,
      label: i.title,
      detail: [i.isbn, i.author, `Stock: ${i.quantity}`, i.active ? null : "Inactive"].filter(Boolean).join(" · "),
      data: i,
    }));
}
