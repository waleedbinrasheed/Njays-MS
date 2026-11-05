import Image from "next/image";

/** Reference pictures (800 px wide) for the built-in dress types. User-added dress types have none. */
const PICTURES: Record<string, { width: number; height: number }> = {
  SHALWAR_KAMEEZ: { width: 800, height: 1035 },
  FORMAL_SHIRT: { width: 800, height: 662 },
  PANT_TROUSER: { width: 800, height: 682 },
  WAISTCOAT: { width: 800, height: 559 },
  COAT_SUIT: { width: 800, height: 630 },
};

export interface MeasurementLine {
  label: string;
  value: number;
  sortOrder: number;
}

/** The reference picture of a built-in dress type. Renders nothing for a dress type the user added. */
export function DressPicture({ code, dressName, compact = false }: { code: string | null; dressName: string; compact?: boolean }) {
  const picture = code ? PICTURES[code] : undefined;
  if (!code || !picture) return null;
  return (
    <Image
      src={`/dress-reference/${code}.jpg`}
      alt={`Reference picture for ${dressName}`}
      width={picture.width}
      height={picture.height}
      unoptimized
      className={`${compact ? "w-28" : "w-44 sm:w-56"} h-auto shrink-0 rounded border border-slate-200 bg-white`}
    />
  );
}

/**
 * The reference picture of a dress type, with its measurements in inches beside it. Stacks vertically
 * on phones. Without a picture (a dress type the user added) only the measurements are shown.
 */
export function DressMeasurements({
  code,
  dressName,
  measurements,
  compact = false,
}: {
  code: string | null;
  dressName: string;
  measurements: MeasurementLine[];
  /** Smaller picture and text, for the two-copy printed invoice. */
  compact?: boolean;
}) {
  const ordered = [...measurements].sort((a, b) => a.sortOrder - b.sortOrder);

  return (
    <div className={`flex flex-col items-start gap-4 sm:flex-row ${compact ? "text-[10px] leading-tight" : "text-sm"}`}>
      <DressPicture code={code} dressName={dressName} compact={compact} />
      <table className="w-full min-w-0 text-sm sm:max-w-sm">
        <thead className="text-left text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th scope="col" className="py-1 pr-2 font-medium">Measurement</th>
            <th scope="col" className="py-1 text-right font-medium">Inches</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {ordered.map((m) => (
            <tr key={`${m.sortOrder}-${m.label}`}>
              <td className="py-1 pr-2 text-slate-900">{m.label}</td>
              <td className="py-1 text-right tabular-nums">{m.value}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
