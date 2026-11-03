import type { Option } from "@/components/ui/AsyncSelect";
import type { FabricSource, OrderLine, OrderLineInput } from "@/types/order";

/** A line being entered. Numbers stay as typed text so fields can be cleared while editing. */
export interface LineDraft {
  key: number;
  garmentType: Option | null;
  fabricSource: FabricSource;
  fabric: Option | null;
  /** Price per metre of the chosen fabric (from the fabric, or the saved line when editing). */
  fabricPricePerMetre: number;
  /** Quantity on hand when known (a fabric picked in this form); null for saved lines. */
  fabricOnHand: number | null;
  fabricMetres: string;
  customerFabricNote: string;
  stitchingCharge: string;
  measurementRecordId: number | null;
  notes: string;
}

export const num = (s: string) => (s.trim() === "" ? 0 : Number(s));

let nextKey = 1;

export function emptyLine(): LineDraft {
  return {
    key: nextKey++,
    garmentType: null,
    fabricSource: "STOCK",
    fabric: null,
    fabricPricePerMetre: 0,
    fabricOnHand: null,
    fabricMetres: "",
    customerFabricNote: "",
    stitchingCharge: "",
    measurementRecordId: null,
    notes: "",
  };
}

export function lineFromSaved(l: OrderLine): LineDraft {
  return {
    key: nextKey++,
    garmentType: { id: l.garmentType.id, label: l.garmentType.name },
    fabricSource: l.fabricSource,
    fabric: l.fabric ? { id: l.fabric.id, label: `${l.fabric.code} · ${l.fabric.name}${l.fabric.colour ? ` · ${l.fabric.colour}` : ""}` } : null,
    fabricPricePerMetre: Number(l.fabricPricePerMetre ?? 0),
    fabricOnHand: null,
    fabricMetres: l.fabricMetres === null ? "" : String(l.fabricMetres),
    customerFabricNote: l.customerFabricNote ?? "",
    stitchingCharge: String(l.stitchingCharge ?? ""),
    measurementRecordId: l.measurementRecordId,
    notes: l.notes ?? "",
  };
}

export function toLineInput(l: LineDraft): OrderLineInput {
  const fromStock = l.fabricSource === "STOCK";
  return {
    garmentTypeId: l.garmentType?.id ?? null,
    fabricSource: l.fabricSource,
    fabricProductId: fromStock ? (l.fabric?.id ?? null) : null,
    fabricMetres: fromStock ? num(l.fabricMetres) : null,
    customerFabricNote: fromStock ? null : l.customerFabricNote.trim() || null,
    stitchingCharge: num(l.stitchingCharge),
    measurementRecordId: l.measurementRecordId,
    notes: l.notes.trim() || null,
  };
}
