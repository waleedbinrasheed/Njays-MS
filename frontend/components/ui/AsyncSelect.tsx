"use client";

import { useEffect, useId, useRef, useState, type KeyboardEvent } from "react";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";

export interface Option<T = unknown> {
  id: number;
  label: string;
  detail?: string;
  /** The full record behind the option, for pickers that need more than id and label. */
  data?: T;
}

interface AsyncSelectProps {
  label: string;
  value: Option | null;
  onChange: (value: Option | null) => void;
  /** Called with the typed text; should return a short list (e.g. first 20 matches). */
  load: (search: string, signal: AbortSignal) => Promise<Option[]>;
  placeholder?: string;
  error?: string;
  required?: boolean;
  clearable?: boolean;
  disabled?: boolean;
}

/** Type-to-search picker backed by the API, so large lists are never loaded into the browser. */
export function AsyncSelect({
  label,
  value,
  onChange,
  load,
  placeholder = "Type to search...",
  error,
  required,
  clearable = true,
  disabled,
}: AsyncSelectProps) {
  const id = useId();
  const [open, setOpen] = useState(false);
  const [text, setText] = useState("");
  const [options, setOptions] = useState<Option[]>([]);
  const [loadedFor, setLoadedFor] = useState<string | null>(null);
  const [highlight, setHighlight] = useState(0);
  const debounced = useDebouncedValue(text, 250);
  const loadRef = useRef(load);
  const onChangeRef = useRef(onChange);
  const containerRef = useRef<HTMLDivElement>(null);
  // Enter pressed before results arrived (e.g. a barcode scanner): pick when they land.
  const pendingEnter = useRef(false);

  useEffect(() => {
    loadRef.current = load;
    onChangeRef.current = onChange;
  });

  useEffect(() => {
    if (!open) return;
    const controller = new AbortController();
    loadRef
      .current(debounced, controller.signal)
      .then((result) => {
        if (pendingEnter.current) {
          pendingEnter.current = false;
          if (result.length === 1) {
            onChangeRef.current(result[0]);
            setText("");
            setOpen(false);
            return;
          }
        }
        setOptions(result);
        setHighlight(0);
        setLoadedFor(debounced);
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setOptions([]);
          setLoadedFor(debounced);
        }
      });
    return () => controller.abort();
  }, [open, debounced]);

  useEffect(() => {
    if (!open) return;
    const onDocClick = (e: MouseEvent) => {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, [open]);

  const loading = open && loadedFor !== debounced;

  function choose(option: Option) {
    onChange(option);
    setText("");
    setOpen(false);
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setOpen(true);
      setHighlight((h) => Math.min(h + 1, options.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setHighlight((h) => Math.max(h - 1, 0));
    } else if (e.key === "Enter" && open) {
      e.preventDefault();
      if (loading || text !== debounced) pendingEnter.current = true;
      else if (options[highlight]) choose(options[highlight]);
    } else if (e.key === "Escape") {
      setOpen(false);
    }
  }

  const border = error ? "border-red-400 focus-within:outline-red-500" : "border-slate-300 focus-within:outline-blue-500";

  return (
    <div className="space-y-1" ref={containerRef}>
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
        {required && <span className="ml-0.5 text-red-500">*</span>}
      </label>
      <div className="relative">
        <div className={`flex items-center rounded-md border bg-white shadow-xs focus-within:outline-2 ${border}`}>
          <input
            id={id}
            role="combobox"
            aria-expanded={open}
            aria-controls={`${id}-list`}
            aria-autocomplete="list"
            aria-invalid={error ? true : undefined}
            disabled={disabled}
            className="w-full rounded-md bg-transparent px-3 py-2 text-sm outline-none placeholder:text-slate-400 disabled:text-slate-500"
            placeholder={value ? value.label : placeholder}
            value={open ? text : (value?.label ?? "")}
            onFocus={() => setOpen(true)}
            onChange={(e) => {
              setText(e.target.value);
              setOpen(true);
            }}
            onKeyDown={onKeyDown}
            autoComplete="off"
          />
          {clearable && value && !disabled && (
            <button
              type="button"
              className="px-2 text-slate-400 hover:text-slate-700"
              onClick={() => onChange(null)}
              aria-label={`Clear ${label}`}
            >
              ×
            </button>
          )}
        </div>
        {open && (
          <ul
            id={`${id}-list`}
            role="listbox"
            className="absolute z-40 mt-1 max-h-60 w-full overflow-y-auto rounded-md border border-slate-200 bg-white py-1 text-sm shadow-lg"
          >
            {loading && <li className="px-3 py-2 text-slate-500">Searching...</li>}
            {!loading && options.length === 0 && <li className="px-3 py-2 text-slate-500">No matches</li>}
            {!loading &&
              options.map((o, i) => (
                <li
                  key={o.id}
                  role="option"
                  aria-selected={value?.id === o.id}
                  className={`cursor-pointer px-3 py-2 ${i === highlight ? "bg-blue-50 text-blue-800" : "text-slate-800"}`}
                  onMouseEnter={() => setHighlight(i)}
                  onMouseDown={(e) => {
                    e.preventDefault();
                    choose(o);
                  }}
                >
                  <div>{o.label}</div>
                  {o.detail && <div className="text-xs text-slate-500">{o.detail}</div>}
                </li>
              ))}
          </ul>
        )}
      </div>
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  );
}
