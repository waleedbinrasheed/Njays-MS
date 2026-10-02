"use client";

import Link from "next/link";
import { useState, type ReactNode } from "react";
import { useSearchParams } from "next/navigation";
import { AsyncSelect, type Option } from "@/components/ui/AsyncSelect";
import { Badge } from "@/components/ui/Feedback";
import { SearchInput, SelectField, TextField } from "@/components/ui/FormField";
import { StatTile } from "@/components/ui/StatTile";
import { DocumentStatusBadge, PaymentStatusBadge } from "@/features/documents/StatusBadges";
import { loadStockedProductOptions } from "@/features/inventory/lookups";
import {
  loadAnyRepresentativeOptions,
  loadCategoryOptions,
  loadCustomerOptions,
  loadPublisherOptions,
  loadSupplierOptions,
} from "@/features/master/lookups";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { reportService } from "@/services/reportService";
import { sampleService } from "@/services/sampleService";
import type { StockStatusFilter, TransactionType } from "@/types/inventory";
import type { DocumentStatus, PaymentStatus } from "@/types/purchase";
import type { DocumentRow, InventoryRow, LowStockRow, MovementRow, RepresentativeRow } from "@/types/report";
import type { SampleReportSummary, SampleRepresentativeRow, SampleStatus } from "@/types/sample";
import { formatCurrency, formatDate, formatDateTime } from "@/utils/format";
import { PAYMENT_STATUS_LABELS, SAMPLE_STATUS_LABELS, TRANSACTION_TYPE_LABELS, formatSignedQuantity } from "@/utils/labels";
import { sampleLineColumns } from "@/features/samples/SampleLinesTable";
import { ReportShell, type ReportColumn } from "./ReportShell";
import { describeFilters, useReport } from "./useReport";

const rangeError = (from: string, to: string) => (from && to && to < from ? "'To' must be on or after 'From'" : undefined);

function DateRange({ from, to, setFrom, setTo }: { from: string; to: string; setFrom: (v: string) => void; setTo: (v: string) => void }) {
  return (
    <>
      <TextField label="Date from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
      <TextField label="Date to" type="date" value={to} onChange={(e) => setTo(e.target.value)} error={rangeError(from, to)} />
    </>
  );
}

// ------------------------------------------------------------------ Inventory report

export function InventoryReportPage() {
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState<Option | null>(null);
  const [publisher, setPublisher] = useState<Option | null>(null);
  const [stock, setStock] = useState<StockStatusFilter | "">("");
  const debounced = useDebouncedValue(search);

  const r = useReport(
    reportService.inventory,
    { search: debounced || undefined, categoryId: category?.id, publisherId: publisher?.id, stock: stock || undefined },
    { field: "title", direction: "asc" },
  );

  const columns: ReportColumn<InventoryRow>[] = [
    { header: "ISBN", sortField: "isbn", render: (i) => <span className="font-mono text-xs">{i.isbn ?? "-"}</span> },
    { header: "Book / product", sortField: "title", render: (i) => i.title },
    { header: "Publisher", sortField: "publisher", render: (i) => i.publisher ?? "-" },
    { header: "Category", sortField: "category", render: (i) => i.category ?? "-" },
    {
      header: "Stock",
      sortField: "quantity",
      align: "right",
      render: (i) => (
        <>
          {i.quantity}
          {i.quantity === 0 ? (
            <span className="ml-1">
              <Badge tone="red">Out</Badge>
            </span>
          ) : i.quantity <= i.reorderLevel ? (
            <span className="ml-1">
              <Badge tone="amber">Low</Badge>
            </span>
          ) : null}
        </>
      ),
    },
    { header: "Reorder level", sortField: "reorderLevel", align: "right", render: (i) => i.reorderLevel },
    { header: "Purchase price", sortField: "purchasePrice", align: "right", render: (i) => formatCurrency(i.purchasePrice) },
    { header: "Selling price", sortField: "sellingPrice", align: "right", render: (i) => formatCurrency(i.sellingPrice) },
    { header: "Stock value", sortField: "stockValue", align: "right", render: (i) => formatCurrency(i.stockValue) },
  ];

  return (
    <ReportShell
      title="Inventory Report"
      description="Stock of active products with cost value (stock × purchase price)."
      filters={
        <>
          <div className="space-y-1">
            <label htmlFor="inv-search" className="block text-sm font-medium text-slate-700">
              Book / product
            </label>
            <SearchInput id="inv-search" placeholder="ISBN, title or author" value={search} onChange={(e) => setSearch(e.target.value)} />
          </div>
          <AsyncSelect label="Category" value={category} onChange={setCategory} load={loadCategoryOptions} placeholder="All categories" />
          <AsyncSelect label="Publisher" value={publisher} onChange={setPublisher} load={loadPublisherOptions} placeholder="All publishers" />
          <SelectField label="Stock" value={stock} onChange={(e) => setStock(e.target.value as StockStatusFilter | "")}>
            <option value="">All</option>
            <option value="LOW">Low stock</option>
            <option value="OUT">Out of stock</option>
            <option value="IN_STOCK">In stock</option>
          </SelectField>
        </>
      }
      filterSummary={describeFilters([
        ["Search", debounced],
        ["Category", category?.label],
        ["Publisher", publisher?.label],
        ["Stock", stock ? { LOW: "Low", OUT: "Out of stock", IN_STOCK: "In stock" }[stock] : null],
      ])}
      summary={
        r.data && (
          <>
            <StatTile label="Products" value={r.data.summary.products.toLocaleString("en-PK")} />
            <StatTile label="Units in stock" value={r.data.summary.units.toLocaleString("en-PK")} />
            <StatTile label="Stock value (cost)" value={formatCurrency(r.data.summary.stockValue)} />
          </>
        )
      }
      columns={columns}
      rows={r.data?.content}
      rowKey={(i) => i.productId}
      loading={r.loading}
      error={r.error}
      onRetry={r.reload}
      sort={r.sort}
      onSort={r.onSort}
      page={r.data}
      onPageChange={r.setPage}
      pageSize={r.size}
      onPageSizeChange={r.setSize}
    />
  );
}

// ------------------------------------------------------------------ Low stock report

export function LowStockReportPage() {
  const [category, setCategory] = useState<Option | null>(null);
  const [publisher, setPublisher] = useState<Option | null>(null);
  const r = useReport(
    reportService.lowStock,
    { categoryId: category?.id, publisherId: publisher?.id },
    { field: "quantity", direction: "asc" },
  );

  const columns: ReportColumn<LowStockRow>[] = [
    { header: "ISBN", render: (i) => <span className="font-mono text-xs">{i.isbn ?? "-"}</span> },
    { header: "Book / product", sortField: "title", render: (i) => i.title },
    { header: "Publisher", sortField: "publisher", render: (i) => i.publisher ?? "-" },
    { header: "Category", sortField: "category", render: (i) => i.category ?? "-" },
    {
      header: "Stock",
      sortField: "quantity",
      align: "right",
      render: (i) => (i.quantity === 0 ? <Badge tone="red">Out</Badge> : <span className="font-medium text-amber-700">{i.quantity}</span>),
    },
    { header: "Reorder level", sortField: "reorderLevel", align: "right", render: (i) => i.reorderLevel },
    { header: "Shortfall", sortField: "shortfall", align: "right", render: (i) => i.shortfall },
    { header: "Last cost", align: "right", render: (i) => formatCurrency(i.purchasePrice) },
  ];

  return (
    <ReportShell
      title="Low Stock Report"
      description="Active products whose stock is at or below their reorder level."
      filters={
        <>
          <AsyncSelect label="Category" value={category} onChange={setCategory} load={loadCategoryOptions} placeholder="All categories" />
          <AsyncSelect label="Publisher" value={publisher} onChange={setPublisher} load={loadPublisherOptions} placeholder="All publishers" />
        </>
      }
      filterSummary={describeFilters([
        ["Category", category?.label],
        ["Publisher", publisher?.label],
      ])}
      summary={
        r.data && (
          <>
            <StatTile label="Products to reorder" value={r.data.summary.products.toLocaleString("en-PK")} />
            <StatTile
              label="Out of stock"
              value={r.data.summary.outOfStock.toLocaleString("en-PK")}
              status={r.data.summary.outOfStock > 0 ? { tone: "critical", text: "Cannot be sold" } : undefined}
            />
            <StatTile label="Units to reach reorder level" value={r.data.summary.unitsToReorder.toLocaleString("en-PK")} />
          </>
        )
      }
      columns={columns}
      rows={r.data?.content}
      rowKey={(i) => i.productId}
      loading={r.loading}
      error={r.error}
      onRetry={r.reload}
      sort={r.sort}
      onSort={r.onSort}
      page={r.data}
      onPageChange={r.setPage}
      pageSize={r.size}
      onPageSizeChange={r.setSize}
    />
  );
}

// ------------------------------------------------------------------ Sales / purchase reports

function DocumentReport({ kind }: { kind: "sales" | "purchases" }) {
  const isSales = kind === "sales";
  const params = useSearchParams();
  // Drill-down from the Representative report: ?representativeId=..&representativeName=..&from=..&to=..
  const linkedRep = Number(params.get("representativeId")) || null;
  const [from, setFrom] = useState(params.get("from") ?? "");
  const [to, setTo] = useState(params.get("to") ?? "");
  const [representative, setRepresentative] = useState<Option | null>(
    linkedRep ? { id: linkedRep, label: params.get("representativeName") ?? `#${linkedRep}` } : null,
  );
  const [party, setParty] = useState<Option | null>(null);
  const [product, setProduct] = useState<Option | null>(null);
  const [payment, setPayment] = useState<PaymentStatus | "">("");
  const [status, setStatus] = useState<DocumentStatus | "">("");
  const blocked = !!rangeError(from, to);

  const r = useReport(
    isSales ? reportService.sales : reportService.purchases,
    {
      from: from || undefined,
      to: to || undefined,
      [isSales ? "customerId" : "supplierId"]: party?.id,
      representativeId: isSales ? representative?.id : undefined,
      productId: product?.id,
      paymentStatus: payment || undefined,
      status: status || undefined,
    },
    { field: "date", direction: "desc" },
    blocked,
  );

  const partyLabel = isSales ? "Customer" : "Supplier";
  const columns: ReportColumn<DocumentRow>[] = [
    {
      header: isSales ? "Invoice number" : "Purchase number",
      sortField: "number",
      render: (d) => (
        <>
          <Link href={`/${kind}/${d.id}`} className="font-mono text-xs font-medium text-blue-700 hover:underline print:text-slate-900">
            {d.number}
          </Link>{" "}
          <DocumentStatusBadge status={d.status} />
        </>
      ),
    },
    { header: "Date", sortField: "date", render: (d) => formatDate(d.date) },
    ...(isSales
      ? [
          {
            header: "Representative",
            sortField: "party",
            render: (d: DocumentRow) => d.party ?? <span className="text-slate-400">Unassigned</span>,
          },
          { header: "Customer", sortField: "customer", render: (d: DocumentRow) => d.customer ?? "-" },
        ]
      : [{ header: partyLabel, sortField: "party", render: (d: DocumentRow) => d.party }]),
    { header: "Items", align: "right", render: (d) => d.items },
    {
      header: "Total",
      sortField: "total",
      align: "right",
      render: (d) => <span className={d.status === "CANCELLED" ? "text-slate-400 line-through" : ""}>{formatCurrency(d.total)}</span>,
    },
    { header: "Payment status", render: (d) => <PaymentStatusBadge status={d.paymentStatus} /> },
    { header: "Created by", render: (d) => d.createdBy },
  ];

  return (
    <ReportShell
      title={isSales ? "Sales Report" : "Purchase Report"}
      description="Totals count completed documents only; cancelled ones are listed but excluded."
      filters={
        <>
          <DateRange from={from} to={to} setFrom={setFrom} setTo={setTo} />
          {isSales && (
            <AsyncSelect
              label="Representative"
              value={representative}
              onChange={setRepresentative}
              load={loadAnyRepresentativeOptions}
              placeholder="All representatives"
            />
          )}
          <AsyncSelect
            label={partyLabel}
            value={party}
            onChange={setParty}
            load={isSales ? loadCustomerOptions : loadSupplierOptions}
            placeholder={`All ${partyLabel.toLowerCase()}s`}
          />
          <AsyncSelect label="Book / product" value={product} onChange={setProduct} load={loadStockedProductOptions} placeholder="All products" />
          <SelectField label="Payment status" value={payment} onChange={(e) => setPayment(e.target.value as PaymentStatus | "")}>
            <option value="">All</option>
            {Object.entries(PAYMENT_STATUS_LABELS).map(([k, label]) => (
              <option key={k} value={k}>
                {label}
              </option>
            ))}
          </SelectField>
          <SelectField label="Status" value={status} onChange={(e) => setStatus(e.target.value as DocumentStatus | "")}>
            <option value="">All</option>
            <option value="COMPLETED">Completed</option>
            <option value="CANCELLED">Cancelled</option>
          </SelectField>
        </>
      }
      filterSummary={describeFilters([
        ["From", from],
        ["To", to],
        ["Representative", isSales ? representative?.label : null],
        [partyLabel, party?.label],
        ["Product", product?.label],
        ["Payment", payment ? PAYMENT_STATUS_LABELS[payment] : null],
        ["Status", status || null],
      ])}
      summary={
        r.data && (
          <>
            <StatTile label={isSales ? "Invoices" : "Purchases"} value={r.data.summary.documents.toLocaleString("en-PK")} />
            <StatTile label="Total" value={formatCurrency(r.data.summary.total)} />
            <StatTile label="Paid" value={formatCurrency(r.data.summary.paid)} />
            <StatTile
              label="Unpaid + partially paid"
              value={formatCurrency(Number(r.data.summary.unpaid) + Number(r.data.summary.partial))}
            />
          </>
        )
      }
      columns={columns}
      rows={r.data?.content}
      rowKey={(d) => d.id}
      loading={r.loading}
      error={r.error}
      onRetry={r.reload}
      sort={r.sort}
      onSort={r.onSort}
      page={r.data}
      onPageChange={r.setPage}
      pageSize={r.size}
      onPageSizeChange={r.setSize}
    />
  );
}

export function SalesReportPage() {
  return <DocumentReport kind="sales" />;
}

export function PurchaseReportPage() {
  return <DocumentReport kind="purchases" />;
}

// ------------------------------------------------------------------ Stock movement report

export function StockMovementReportPage() {
  const [product, setProduct] = useState<Option | null>(null);
  const [type, setType] = useState<TransactionType | "">("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const blocked = !!rangeError(from, to);

  const r = useReport(
    reportService.stockMovement,
    { productId: product?.id, type: type || undefined, from: from || undefined, to: to || undefined },
    { field: "date", direction: "desc" },
    blocked,
  );

  const columns: ReportColumn<MovementRow>[] = [
    { header: "Date", sortField: "date", render: (m) => <span className="whitespace-nowrap">{formatDateTime(m.date)}</span> },
    {
      header: "Product",
      sortField: "product",
      render: (m) => (
        <>
          <div>{m.product}</div>
          {m.isbn && <div className="font-mono text-xs text-slate-500">{m.isbn}</div>}
        </>
      ),
    },
    { header: "Transaction type", render: (m) => TRANSACTION_TYPE_LABELS[m.type] },
    {
      header: "Quantity",
      sortField: "quantity",
      align: "right",
      render: (m) => <span className={m.quantity > 0 ? "text-emerald-700" : "text-red-600"}>{formatSignedQuantity(m.quantity)}</span>,
    },
    { header: "Previous", align: "right", render: (m) => m.previousQuantity },
    { header: "Resulting", align: "right", render: (m) => m.resultingQuantity },
    { header: "Reference", render: (m) => <span className="font-mono text-xs">{m.reference ?? "-"}</span> },
    { header: "User", render: (m) => m.user },
  ];

  return (
    <ReportShell
      title="Stock Movement Report"
      description="Every stock change with before and after quantities."
      filters={
        <>
          <AsyncSelect label="Book / product" value={product} onChange={setProduct} load={loadStockedProductOptions} placeholder="All products" />
          <SelectField label="Transaction type" value={type} onChange={(e) => setType(e.target.value as TransactionType | "")}>
            <option value="">All</option>
            {Object.entries(TRANSACTION_TYPE_LABELS).map(([k, label]) => (
              <option key={k} value={k}>
                {label}
              </option>
            ))}
          </SelectField>
          <DateRange from={from} to={to} setFrom={setFrom} setTo={setTo} />
        </>
      }
      filterSummary={describeFilters([
        ["Product", product?.label],
        ["Type", type ? TRANSACTION_TYPE_LABELS[type] : null],
        ["From", from],
        ["To", to],
      ])}
      summary={
        r.data && (
          <>
            <StatTile label="Movements" value={r.data.summary.movements.toLocaleString("en-PK")} />
            <StatTile label="Units in" value={`+${r.data.summary.unitsIn.toLocaleString("en-PK")}`} />
            <StatTile label="Units out" value={`−${r.data.summary.unitsOut.toLocaleString("en-PK")}`} />
          </>
        )
      }
      columns={columns}
      rows={r.data?.content}
      rowKey={(m) => m.id}
      loading={r.loading}
      error={r.error}
      onRetry={r.reload}
      sort={r.sort}
      onSort={r.onSort}
      page={r.data}
      onPageChange={r.setPage}
      pageSize={r.size}
      onPageSizeChange={r.setSize}
    />
  );
}

// ------------------------------------------------------------------ Representative report

export function RepresentativeReportPage() {
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [representative, setRepresentative] = useState<Option | null>(null);
  const [area, setArea] = useState("");
  const [active, setActive] = useState<"" | "true" | "false">("");
  const debouncedArea = useDebouncedValue(area);
  const blocked = !!rangeError(from, to);

  const r = useReport(
    reportService.representatives,
    {
      from: from || undefined,
      to: to || undefined,
      representativeId: representative?.id,
      area: debouncedArea || undefined,
      active: active === "" ? undefined : active === "true",
    },
    { field: "netSales", direction: "desc" },
    blocked,
  );

  const drillDown = (row: RepresentativeRow) => {
    if (row.id === null) return null;
    const q = new URLSearchParams({ representativeId: String(row.id), representativeName: row.name });
    if (from) q.set("from", from);
    if (to) q.set("to", to);
    return `/reports/sales?${q.toString()}`;
  };

  const columns: ReportColumn<RepresentativeRow>[] = [
    {
      header: "Representative",
      sortField: "name",
      render: (row) => {
        const href = drillDown(row);
        return (
          <>
            {href ? (
              <Link href={href} className="font-medium text-blue-700 hover:underline print:text-slate-900">
                {row.name}
              </Link>
            ) : (
              <span className="text-slate-500" title="Invoices from before representatives were introduced">
                {row.name}
              </span>
            )}
            {!row.active && (
              <span className="ml-1">
                <Badge tone="red">Inactive</Badge>
              </span>
            )}
          </>
        );
      },
    },
    { header: "Area", sortField: "area", render: (row) => row.area ?? "-" },
    { header: "Customers", sortField: "customers", align: "right", render: (row) => row.customers },
    { header: "Invoices", sortField: "invoices", align: "right", render: (row) => row.invoices },
    { header: "Sales", sortField: "salesTotal", align: "right", render: (row) => formatCurrency(row.salesTotal) },
    { header: "Returns", sortField: "returns", align: "right", render: (row) => (Number(row.returns) ? `− ${formatCurrency(row.returns)}` : "-") },
    { header: "Net sales", sortField: "netSales", align: "right", render: (row) => <strong>{formatCurrency(row.netSales)}</strong> },
    { header: "Paid", align: "right", render: (row) => formatCurrency(row.paid) },
    {
      header: "Unpaid + partial",
      sortField: "owed",
      align: "right",
      render: (row) => <span className={Number(row.owed) > 0 ? "text-amber-700" : ""}>{formatCurrency(row.owed)}</span>,
    },
    { header: "Last sale", sortField: "lastSale", render: (row) => formatDate(row.lastSale) },
  ];

  return (
    <ReportShell
      title="Representative Report"
      description="Sales billed to each representative: completed invoices and returns dated in the period. Click a name for their invoices."
      filters={
        <>
          <DateRange from={from} to={to} setFrom={setFrom} setTo={setTo} />
          <AsyncSelect
            label="Representative"
            value={representative}
            onChange={setRepresentative}
            load={loadAnyRepresentativeOptions}
            placeholder="All representatives"
          />
          <TextField label="Area" value={area} onChange={(e) => setArea(e.target.value)} placeholder="Any area" />
          <SelectField label="Status" value={active} onChange={(e) => setActive(e.target.value as "" | "true" | "false")}>
            <option value="">All</option>
            <option value="true">Active</option>
            <option value="false">Inactive</option>
          </SelectField>
        </>
      }
      filterSummary={describeFilters([
        ["From", from],
        ["To", to],
        ["Representative", representative?.label],
        ["Area", debouncedArea],
        ["Status", active === "" ? null : active === "true" ? "Active" : "Inactive"],
      ])}
      summary={
        r.data && (
          <>
            <StatTile label="Representatives" value={r.data.summary.representatives.toLocaleString("en-PK")} />
            <StatTile label="Invoices" value={r.data.summary.invoices.toLocaleString("en-PK")} />
            <StatTile
              label="Net sales"
              value={formatCurrency(r.data.summary.netSales)}
              detail={`Sales ${formatCurrency(r.data.summary.salesTotal)} − returns ${formatCurrency(r.data.summary.returns)}`}
            />
            <StatTile
              label="Unpaid + partially paid"
              value={formatCurrency(r.data.summary.owed)}
              status={Number(r.data.summary.owed) > 0 ? { tone: "warning", text: "Outstanding" } : undefined}
            />
          </>
        )
      }
      columns={columns}
      rows={r.data?.content}
      rowKey={(row) => row.id ?? -1}
      loading={r.loading}
      error={r.error}
      onRetry={r.reload}
      sort={r.sort}
      onSort={r.onSort}
      page={r.data}
      onPageChange={r.setPage}
      pageSize={r.size}
      onPageSizeChange={r.setSize}
    />
  );
}

// ------------------------------------------------------------------ Sample report

type SampleView = "representatives" | "lines";

interface SampleViewProps {
  filters: Record<string, string | number | undefined>;
  blocked: boolean;
  filterNode: ReactNode;
  filterSummary: string;
}

/** Issued / returned / outstanding samples, per representative or per issued book. Not part of sales. */
export function SampleReportPage() {
  const [view, setView] = useState<SampleView>("representatives");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [representative, setRepresentative] = useState<Option | null>(null);
  const [product, setProduct] = useState<Option | null>(null);
  const [status, setStatus] = useState<SampleStatus | "">("");

  const props: SampleViewProps = {
    filters: {
      from: from || undefined,
      to: to || undefined,
      representativeId: representative?.id,
      productId: product?.id,
      status: status || undefined,
    },
    blocked: !!rangeError(from, to),
    filterNode: (
      <>
        <SelectField label="View" value={view} onChange={(e) => setView(e.target.value as SampleView)}>
          <option value="representatives">By representative</option>
          <option value="lines">Detail (each book issued)</option>
        </SelectField>
        <DateRange from={from} to={to} setFrom={setFrom} setTo={setTo} />
        <AsyncSelect label="Representative" value={representative} onChange={setRepresentative} load={loadAnyRepresentativeOptions} placeholder="All representatives" />
        <AsyncSelect label="Book" value={product} onChange={setProduct} load={loadStockedProductOptions} placeholder="All books" />
        <SelectField label="Status" value={status} onChange={(e) => setStatus(e.target.value as SampleStatus | "")}>
          <option value="">All</option>
          {Object.entries(SAMPLE_STATUS_LABELS).map(([k, label]) => (
            <option key={k} value={k}>
              {label}
            </option>
          ))}
        </SelectField>
      </>
    ),
    filterSummary: describeFilters([
      ["View", view === "representatives" ? "By representative" : "Detail"],
      ["Issued from", from],
      ["Issued to", to],
      ["Representative", representative?.label],
      ["Book", product?.label],
      ["Status", status ? SAMPLE_STATUS_LABELS[status] : null],
    ]),
  };

  return view === "representatives" ? <SampleRepresentativeView {...props} /> : <SampleLineView {...props} />;
}

function SampleSummaryTiles({ s, showRepresentatives }: { s: SampleReportSummary; showRepresentatives: boolean }) {
  return (
    <>
      <StatTile
        label={showRepresentatives ? "Representatives / samples" : "Samples"}
        value={showRepresentatives ? `${s.rows.toLocaleString("en-PK")} / ${s.samples.toLocaleString("en-PK")}` : s.samples.toLocaleString("en-PK")}
      />
      <StatTile label="Issued" value={formatCurrency(s.issuedAmount)} detail={`${s.issuedQuantity.toLocaleString("en-PK")} unit(s)`} />
      <StatTile label="Returned" value={formatCurrency(s.returnedAmount)} detail={`${s.returnedQuantity.toLocaleString("en-PK")} unit(s)`} />
      <StatTile
        label="Outstanding"
        value={formatCurrency(s.outstandingAmount)}
        detail={`${s.outstandingQuantity.toLocaleString("en-PK")} unit(s) still with representatives`}
        status={s.outstandingQuantity > 0 ? { tone: "warning", text: "Out" } : undefined}
      />
    </>
  );
}

const SAMPLE_DESCRIPTION =
  "Samples given to representatives (SAMPLE SALE, not retail). Date filters use the issue date; cancelled samples are not counted.";

function SampleRepresentativeView({ filters, blocked, filterNode, filterSummary }: SampleViewProps) {
  const r = useReport(sampleService.reportRepresentatives, filters, { field: "outstandingAmount", direction: "desc" }, blocked);
  const columns: ReportColumn<SampleRepresentativeRow>[] = [
    {
      header: "Representative",
      sortField: "name",
      render: (row) => (
        <>
          <Link href={`/master/representatives/${row.id}`} className="font-medium text-blue-700 hover:underline print:text-slate-900">
            {row.name}
          </Link>
          {!row.active && (
            <span className="ml-1">
              <Badge tone="red">Inactive</Badge>
            </span>
          )}
        </>
      ),
    },
    { header: "Area", sortField: "area", render: (row) => row.area ?? "-" },
    { header: "Samples", sortField: "samples", align: "right", render: (row) => row.samples },
    { header: "Issued qty", align: "right", render: (row) => row.issuedQuantity },
    { header: "Issued amount", sortField: "issuedAmount", align: "right", render: (row) => formatCurrency(row.issuedAmount) },
    { header: "Returned qty", align: "right", render: (row) => row.returnedQuantity },
    { header: "Returned amount", sortField: "returnedAmount", align: "right", render: (row) => formatCurrency(row.returnedAmount) },
    { header: "Outstanding qty", sortField: "outstandingQuantity", align: "right", render: (row) => <strong>{row.outstandingQuantity}</strong> },
    {
      header: "Outstanding amount",
      sortField: "outstandingAmount",
      align: "right",
      render: (row) => (
        <strong className={Number(row.outstandingAmount) > 0 ? "text-amber-700 print:text-slate-900" : ""}>
          {formatCurrency(row.outstandingAmount)}
        </strong>
      ),
    },
    { header: "Last issue", sortField: "lastIssue", render: (row) => formatDate(row.lastIssue) },
  ];

  return (
    <ReportShell
      title="Sample Report"
      description={SAMPLE_DESCRIPTION}
      filters={filterNode}
      filterSummary={filterSummary}
      summary={r.data && <SampleSummaryTiles s={r.data.summary} showRepresentatives />}
      columns={columns}
      rows={r.data?.content}
      rowKey={(row) => row.id}
      loading={r.loading}
      error={r.error}
      onRetry={r.reload}
      sort={r.sort}
      onSort={r.onSort}
      page={r.data}
      onPageChange={r.setPage}
      pageSize={r.size}
      onPageSizeChange={r.setSize}
    />
  );
}

function SampleLineView({ filters, blocked, filterNode, filterSummary }: SampleViewProps) {
  const r = useReport(sampleService.reportLines, filters, { field: "date", direction: "desc" }, blocked);
  return (
    <ReportShell
      title="Sample Report"
      description={SAMPLE_DESCRIPTION}
      filters={filterNode}
      filterSummary={filterSummary}
      summary={r.data && <SampleSummaryTiles s={r.data.summary} showRepresentatives={false} />}
      columns={sampleLineColumns(true)}
      rows={r.data?.content}
      rowKey={(row) => row.itemId}
      loading={r.loading}
      error={r.error}
      onRetry={r.reload}
      sort={r.sort}
      onSort={r.onSort}
      page={r.data}
      onPageChange={r.setPage}
      pageSize={r.size}
      onPageSizeChange={r.setSize}
    />
  );
}
