/**
 * The customer (e.g. a school) on a sales or sample invoice: name with contact number, then address,
 * as saved with the document. Missing details are simply left out.
 */
export function CustomerBlock({ name, phone, address }: { name: string; phone: string | null; address: string | null }) {
  return (
    <div className="mt-2">
      <p className="text-slate-600">
        School: <span className="font-medium text-slate-900">{name}</span>
        {phone ? ` · ${phone}` : ""}
      </p>
      {address && <p className="whitespace-pre-line text-slate-600">{address}</p>}
    </div>
  );
}
