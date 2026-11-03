"use client";

import Link from "next/link";
import { useState } from "react";
import { type Option } from "@/components/ui/AsyncSelect";
import { Button } from "@/components/ui/Button";
import { EmptyState, ErrorBanner, PageHeader } from "@/components/ui/Feedback";
import { SearchInput } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { DressMeasurements } from "@/components/measurements/DressMeasurements";
import { useApiQuery } from "@/hooks/useApiQuery";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { measurementService } from "@/services/masterDataService";
import type { MeasurementRecord } from "@/types/master";
import { formatDate } from "@/utils/format";
import { FilterSelect } from "./FilterSelect";
import { loadGarmentTypeOptions } from "./lookups";

const PAGE_SIZE = 20;

/** Every measurement record across customers; records are read-only (take them on the customer page). */
export function MeasurementsPage() {
  const [search, setSearch] = useState("");
  const [garment, setGarment] = useState<Option | null>(null);
  const [page, setPage] = useState(0);
  const [viewing, setViewing] = useState<MeasurementRecord | null>(null);
  const debouncedSearch = useDebouncedValue(search);

  const list = useApiQuery(
    (signal) =>
      measurementService.list(
        {
          search: debouncedSearch,
          garmentTypeId: garment?.id,
          page,
          size: PAGE_SIZE,
          sort: "takenOn,desc",
        },
        signal,
      ),
    [debouncedSearch, garment?.id ?? null, page],
  );
  const rows = list.data?.content ?? [];

  return (
    <>
      <PageHeader title="Measurements" description="All measurement records. Take new measurements from a customer's page." />

      <div className="mb-4 flex flex-col flex-wrap gap-2 sm:flex-row sm:items-center">
        <SearchInput
          placeholder="Search by customer name"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          aria-label="Search measurements by customer"
        />
        <div className="sm:w-60">
          <FilterSelect
            label="Dress type"
            placeholder="All dress types"
            value={garment}
            onChange={(o) => {
              setGarment(o);
              setPage(0);
            }}
            load={loadGarmentTypeOptions}
          />
        </div>
        {list.loading && list.data && <Spinner size="sm" />}
      </div>

      {list.error && (
        <div className="mb-4">
          <ErrorBanner message={list.error} onRetry={list.reload} />
        </div>
      )}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="px-4 py-2.5 font-medium">Taken on</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Customer</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Dress</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Notes</th>
                <th scope="col" className="px-4 py-2.5">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((r) => (
                <tr key={r.id} className="hover:bg-slate-50">
                  <td className="whitespace-nowrap px-4 py-2.5">{formatDate(r.takenOn)}</td>
                  <td className="px-4 py-2.5">
                    <Link href={`/master/customers/${r.customerId}`} className="font-medium text-blue-700 hover:underline">
                      {r.customerName}
                    </Link>
                  </td>
                  <td className="px-4 py-2.5">{r.garmentType.name}</td>
                  <td className="px-4 py-2.5 text-slate-600">{r.notes ?? "-"}</td>
                  <td className="whitespace-nowrap px-4 py-2.5 text-right">
                    <Button size="sm" variant="ghost" onClick={() => setViewing(r)}>
                      View
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {list.loading && !list.data && (
          <div className="flex justify-center py-10 text-blue-600">
            <Spinner label="Loading measurements..." />
          </div>
        )}
        {!list.loading && list.data && rows.length === 0 && (
          <EmptyState title="No measurement records found" description={search || garment ? "Try a different search or filter." : undefined} />
        )}
        {list.data && (
          <Pagination
            page={list.data.page}
            size={list.data.size}
            totalElements={list.data.totalElements}
            totalPages={list.data.totalPages}
            onPageChange={setPage}
          />
        )}
      </div>

      <Modal
        open={viewing !== null}
        size="lg"
        title={viewing ? `${viewing.garmentType.name} · ${viewing.customerName}` : ""}
        onClose={() => setViewing(null)}
        footer={
          <Button variant="secondary" onClick={() => setViewing(null)}>
            Close
          </Button>
        }
      >
        {viewing && (
          <div className="space-y-3 text-sm">
            <p className="text-slate-600">
              Taken {formatDate(viewing.takenOn)} by {viewing.createdBy}
              {viewing.notes ? ` · ${viewing.notes}` : ""}
            </p>
            <DressMeasurements code={viewing.garmentType.code} dressName={viewing.garmentType.name} measurements={viewing.values} />
          </div>
        )}
      </Modal>
    </>
  );
}
