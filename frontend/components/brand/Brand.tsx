import Image from "next/image";
import logo from "@/assets/logo.jpg";

export const BRAND_NAME = "Njay's Tailoring";

/**
 * The shop logo. Loaded eagerly so it is always present when a page is printed
 * (lazy images that are off-screen may be missing from the printout).
 * Served as-is (25 KB) so it never depends on the image optimizer, e.g. on an offline LAN server.
 */
export function BrandLogo({ className = "h-10 w-auto" }: { className?: string }) {
  return <Image src={logo} alt={BRAND_NAME} className={className} loading="eager" unoptimized />;
}
