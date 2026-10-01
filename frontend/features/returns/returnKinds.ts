import type { PermissionCode } from "@/types/auth";
import type { ReturnKind, ReturnableLine } from "@/types/returns";

/** Wording and routes that differ between sales returns and purchase returns. */
export interface ReturnKindConfig {
  kind: ReturnKind;
  title: string;
  documentLabel: string;
  partyLabel: string;
  amountLabel: string;
  stockEffect: string;
  viewPermission: PermissionCode;
  documentHref: (id: number) => string;
  historyHref: string;
  detailHref: (id: number) => string;
  printHeading: string;
}

export const RETURN_KINDS: Record<ReturnKind, ReturnKindConfig> = {
  sale: {
    kind: "sale",
    title: "Sales Returns",
    documentLabel: "Invoice",
    partyLabel: "Representative",
    amountLabel: "Refund",
    stockEffect: "added back to stock",
    viewPermission: "SALE_VIEW",
    documentHref: (id) => `/sales/${id}`,
    historyHref: "/sales/returns",
    detailHref: (id) => `/sales/returns/${id}`,
    printHeading: "Sales return / credit note",
  },
  purchase: {
    kind: "purchase",
    title: "Purchase Returns",
    documentLabel: "Purchase",
    partyLabel: "Supplier",
    amountLabel: "Credit",
    stockEffect: "removed from stock",
    viewPermission: "PURCHASE_VIEW",
    documentHref: (id) => `/purchases/${id}`,
    historyHref: "/purchases/returns",
    detailHref: (id) => `/purchases/returns/${id}`,
    printHeading: "Purchase return / debit note",
  },
};

/** Display-only mirror of the backend ReturnMath (share of line total; exact remainder on the last units). */
export function previewReturnAmount(line: ReturnableLine, quantity: number): number {
  if (quantity <= 0) return 0;
  const toPaisa = (v: number) => Math.round(v * 100);
  if (quantity >= line.originalQuantity - line.returnedQuantity) {
    return (toPaisa(line.lineTotal) - toPaisa(line.returnedAmount)) / 100;
  }
  return Math.round((toPaisa(line.lineTotal) * quantity) / line.originalQuantity) / 100;
}
