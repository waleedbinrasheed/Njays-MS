import type { GarmentShape } from "@/types/master";

/** Silhouette outlines on a 120 x 160 grid (inline SVG, so it prints and needs no image files). */
const OUTLINE: Record<GarmentShape, string> = {
  TOP: "M42 12 L52 6 Q60 14 68 6 L78 12 L104 30 L96 64 L86 60 L86 152 L34 152 L34 60 L24 64 L16 30 Z",
  BOTTOM: "M28 8 L92 8 L98 152 L70 152 L60 62 L50 152 L22 152 Z",
  FULL: "M46 8 L74 8 L82 40 L94 152 L26 152 L38 40 Z",
};

/** Fixed marker positions per silhouette. Points beyond these markers appear in the legend only. */
const MARKERS: Record<GarmentShape, [number, number][]> = {
  TOP: [
    [60, 24],
    [60, 60],
    [60, 96],
    [60, 130],
    [26, 44],
    [94, 44],
    [90, 90],
    [30, 90],
  ],
  BOTTOM: [
    [60, 24],
    [42, 60],
    [78, 60],
    [60, 100],
    [36, 130],
    [84, 130],
    [60, 140],
    [52, 84],
  ],
  FULL: [
    [60, 22],
    [60, 52],
    [60, 90],
    [60, 130],
    [40, 46],
    [80, 46],
    [40, 84],
    [80, 84],
  ],
};

export interface PictorialMeasurement {
  label: string;
  value: number;
  sortOrder: number;
}

/** Just the silhouette, used by the shape picker. */
export function GarmentSilhouette({ shape, className = "h-24 w-auto" }: { shape: GarmentShape; className?: string }) {
  return (
    <svg viewBox="0 0 120 160" className={className} aria-hidden>
      <path d={OUTLINE[shape]} fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
    </svg>
  );
}

/**
 * Garment outline with numbered markers and a legend (number, label, value in inches). Stacks
 * vertically on phones. The same numbers appear in the legend, so it reads without colour.
 */
export function GarmentPictorial({
  shape,
  measurements,
  compact = false,
}: {
  shape: GarmentShape;
  measurements: PictorialMeasurement[];
  /** Smaller drawing and text, for the two-copy printed invoice. */
  compact?: boolean;
}) {
  const ordered = [...measurements].sort((a, b) => a.sortOrder - b.sortOrder);
  const markers = MARKERS[shape];

  return (
    <div className={`flex flex-col items-start gap-4 sm:flex-row ${compact ? "text-[10px] leading-tight" : "text-sm"}`}>
      <svg viewBox="0 0 120 160" className={`${compact ? "h-24" : "h-44"} w-auto shrink-0 text-slate-400`} role="img" aria-label="Measurement pictorial">
        <path d={OUTLINE[shape]} fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
        {ordered.slice(0, markers.length).map((m, i) => {
          const [x, y] = markers[i];
          return (
            <g key={`${m.label}-${i}`}>
              <circle cx={x} cy={y} r="8" fill="#2563eb" />
              <text x={x} y={y + 3.5} textAnchor="middle" fontSize="10" fontWeight="700" fill="#ffffff">
                {i + 1}
              </text>
            </g>
          );
        })}
      </svg>
      <table className="w-full min-w-0 text-sm sm:max-w-xs">
        <thead className="text-left text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th scope="col" className="py-1 pr-2 font-medium">No.</th>
            <th scope="col" className="py-1 pr-2 font-medium">Measurement</th>
            <th scope="col" className="py-1 text-right font-medium">Inches</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {ordered.map((m, i) => (
            <tr key={`${m.label}-${i}`}>
              <td className="py-1 pr-2 tabular-nums text-slate-500">{i < markers.length ? i + 1 : "-"}</td>
              <td className="py-1 pr-2 text-slate-900">{m.label}</td>
              <td className="py-1 text-right tabular-nums">{m.value}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
