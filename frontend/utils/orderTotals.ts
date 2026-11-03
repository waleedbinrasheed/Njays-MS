/**
 * Display-only totals for the order form. The backend recalculates every amount on save and its
 * figures are the ones stored and printed; this only previews them while the form is being filled.
 */

const toPaisa = (rupees: number) => Math.round((Number.isFinite(rupees) ? rupees : 0) * 100);
const toRupees = (paisa: number) => paisa / 100;

export interface LineAmountInput {
  fabricFromStock: boolean;
  fabricMetres: number;
  fabricPricePerMetre: number;
  stitchingCharge: number;
}

/** Fabric (metres x price per metre, when from stock) plus stitching, rounded to paisa. */
export function lineAmounts(line: LineAmountInput): { fabricAmount: number; lineTotal: number } {
  const fabricPaisa = line.fabricFromStock
    ? Math.round(toPaisa(line.fabricPricePerMetre) * (Number.isFinite(line.fabricMetres) ? line.fabricMetres : 0))
    : 0;
  const fabricAmount = toRupees(fabricPaisa);
  return { fabricAmount, lineTotal: toRupees(fabricPaisa + toPaisa(line.stitchingCharge)) };
}

export function orderTotal(lines: { lineTotal: number }[]): number {
  return toRupees(lines.reduce((sum, l) => sum + toPaisa(l.lineTotal), 0));
}
