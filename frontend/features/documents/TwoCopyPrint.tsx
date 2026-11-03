"use client";

import type { ReactNode } from "react";

/**
 * Prints the document twice on one A4 page (office copy on top, customer copy below, dashed cut
 * line between). If a copy does not fit in half a page, each copy gets its own page instead.
 * The two-copy block is laid out off-screen at print width so it can be measured before printing.
 */
export function printTwoCopies() {
  const root = document.documentElement;
  const halves = Array.from(document.querySelectorAll<HTMLElement>("[data-two-copy] .copy-half"));
  // Keep ~2mm spare: printed text can come out a little taller than on screen.
  const overflows = halves.some((h) => {
    const contentHeight = (h.firstElementChild as HTMLElement | null)?.offsetHeight ?? 0;
    return contentHeight > h.clientHeight - 8;
  });
  root.dataset.printMode = overflows ? "two-full" : "two";
  const reset = () => {
    delete root.dataset.printMode;
    window.removeEventListener("afterprint", reset);
  };
  window.addEventListener("afterprint", reset);
  window.print();
}

/**
 * Off-screen until "Print 2 copies". `renderCopy` gets the copy label ("Office copy" /
 * "Customer copy"). The on-screen document (marked print-single) is what prints otherwise.
 */
export function TwoCopyPrint({ renderCopy }: { renderCopy: (copyLabel: string) => ReactNode }) {
  return (
    <div data-two-copy className="two-copy-print" aria-hidden>
      <div className="copy-half">{renderCopy("Office copy")}</div>
      <div className="cut-line">
        <span aria-hidden>✂</span>
        <span className="cut-line-rule" />
        <span>cut here</span>
        <span className="cut-line-rule" />
      </div>
      <div className="copy-half">{renderCopy("Customer copy")}</div>
    </div>
  );
}
