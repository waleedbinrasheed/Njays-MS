"use client";

import { useEffect, useId, useRef, useState, type KeyboardEvent, type UIEvent } from "react";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";

export interface Option<T = unknown> {
  id: number;
  label: string;
  detail?: string;
  /** The full record behind the option, for pickers that need more than id and label. */
  data?: T;
}

/** One page of options and whether the server has more after it. */
export interface OptionPage {
  options: Option[];
  hasMore: boolean;
}

/** Loads one page (zero-based) of options matching the typed text. */
export type OptionLoader = (search: string, signal: AbortSignal, page: number) => Promise<OptionPage>;

interface AsyncSelectProps {
  label: string;
  value: Option | null;
  onChange: (value: Option | null) => void;
  load: OptionLoader;
  placeholder?: string;
  error?: string;
  required?: boolean;
  clearable?: boolean;
  disabled?: boolean;
  /**
   * Multi-select use: after a pick the list stays open, the search text is cleared and the cursor
   * stays in the box, so several items can be picked in one go. Clicking outside, Escape or Tab closes it.
   */
  keepOpenOnSelect?: boolean;
  /** Ids already chosen elsewhere (e.g. items on the invoice); they are marked with a tick in the list. */
  selectedIds?: number[];
}

/** Load the next page when the list is scrolled to within this many pixels of the bottom. */
const LOAD_MORE_THRESHOLD = 40;

/**
 * Type-to-search picker backed by the API, so large lists are never loaded into the browser all at
 * once: the first page arrives when the list opens, and further pages as the user scrolls (or
 * arrows) to the end of the list.
 */
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
  keepOpenOnSelect = false,
  selectedIds,
}: AsyncSelectProps) {
  const id = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [text, setText] = useState("");
  const [options, setOptions] = useState<Option[]>([]);
  const [loadedFor, setLoadedFor] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [highlight, setHighlight] = useState(0);
  const debounced = useDebouncedValue(text, 250);
  const loadRef = useRef(load);
  const onChangeRef = useRef(onChange);
  const containerRef = useRef<HTMLDivElement>(null);
  const moreController = useRef<AbortController | null>(null);
  // Enter pressed before results arrived (e.g. a barcode scanner): pick when they land.
  const pendingEnter = useRef(false);

  const keepOpenRef = useRef(keepOpenOnSelect);

  useEffect(() => {
    loadRef.current = load;
    onChangeRef.current = onChange;
    keepOpenRef.current = keepOpenOnSelect;
  });

  // First page whenever the list opens or the search text changes.
  useEffect(() => {
    if (!open) return;
    const controller = new AbortController();
    moreController.current?.abort();
    loadRef
      .current(debounced, controller.signal, 0)
      .then((result) => {
        if (pendingEnter.current) {
          pendingEnter.current = false;
          if (result.options.length === 1) {
            onChangeRef.current(result.options[0]);
            setText("");
            if (!keepOpenRef.current) setOpen(false);
            return;
          }
        }
        setOptions(result.options);
        setHasMore(result.hasMore);
        setPage(0);
        setLoadingMore(false);
        setHighlight(0);
        setLoadedFor(debounced);
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setOptions([]);
          setHasMore(false);
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

  useEffect(() => () => moreController.current?.abort(), []);

  const loading = open && loadedFor !== debounced;

  /** Appends the next page (skipping anything already listed, in case records changed meanwhile). */
  function loadMore() {
    if (!hasMore || loadingMore || loading || loadedFor === null) return;
    const controller = new AbortController();
    moreController.current = controller;
    const search = loadedFor;
    const next = page + 1;
    setLoadingMore(true);
    loadRef
      .current(search, controller.signal, next)
      .then((result) => {
        setOptions((current) => {
          const seen = new Set(current.map((o) => o.id));
          return [...current, ...result.options.filter((o) => !seen.has(o.id))];
        });
        setHasMore(result.hasMore);
        setPage(next);
        setLoadingMore(false);
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setHasMore(false);
          setLoadingMore(false);
        }
      });
  }

  function onListScroll(e: UIEvent<HTMLUListElement>) {
    const el = e.currentTarget;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - LOAD_MORE_THRESHOLD) loadMore();
  }

  function choose(option: Option) {
    onChange(option);
    setText("");
    if (keepOpenOnSelect) {
      setOpen(true);
      inputRef.current?.focus();
    } else {
      setOpen(false);
    }
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setOpen(true);
      if (highlight >= options.length - 2) loadMore();
      setHighlight((h) => Math.min(h + 1, options.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setHighlight((h) => Math.max(h - 1, 0));
    } else if (e.key === "Enter" && open) {
      e.preventDefault();
      if (loading || text !== debounced) pendingEnter.current = true;
      else if (options[highlight]) choose(options[highlight]);
    } else if (e.key === "Escape" || e.key === "Tab") {
      // Escape and Tab-away close the list (Tab is not prevented, so focus moves on as usual).
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
            ref={inputRef}
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
            onScroll={onListScroll}
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
                  <div className="flex items-start gap-2">
                    {selectedIds?.includes(o.id) && (
                      <span className="text-emerald-600" aria-label="Already added" title="Already added">
                        ✓
                      </span>
                    )}
                    <div className="min-w-0">
                      <div>{o.label}</div>
                      {o.detail && <div className="text-xs text-slate-500">{o.detail}</div>}
                    </div>
                  </div>
                </li>
              ))}
            {!loading && hasMore && (
              <li role="presentation" className="px-3 py-2 text-center text-xs text-slate-500">
                {loadingMore ? (
                  "Loading more..."
                ) : (
                  <button type="button" className="text-blue-700 hover:underline" onMouseDown={(e) => e.preventDefault()} onClick={loadMore}>
                    Show more
                  </button>
                )}
              </li>
            )}
          </ul>
        )}
      </div>
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  );
}
