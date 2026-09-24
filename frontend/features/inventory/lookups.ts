import type { Option } from "@/components/ui/AsyncSelect";
import { inventoryService } from "@/services/inventoryService";

/** Product picker that also shows current stock; searches ISBN, title and author. */
export async function loadStockedProductOptions(search: string, signal: AbortSignal): Promise<Option[]> {
  return loadProducts(search, signal, undefined);
}

/** Only products that can be bought or sold (active). */
export async function loadActiveProductOptions(search: string, signal: AbortSignal): Promise<Option[]> {
  return loadProducts(search, signal, true);
}

async function loadProducts(search: string, signal: AbortSignal, active: boolean | undefined): Promise<Option[]> {
  const page = await inventoryService.list({ search, active, size: 20, sort: "title,asc" }, signal);
  return page.content.map((i) => ({
    id: i.productId,
    label: i.title,
    detail: [i.isbn, i.author, `Stock: ${i.quantity}`, i.active ? null : "Inactive"].filter(Boolean).join(" · "),
    data: i,
  }));
}
