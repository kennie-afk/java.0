"use client";

import { useEffect, useRef, useState } from "react";
import { inputClass } from "@/components/ui";

export type LookupKind = "products" | "customers" | "suppliers";

interface Option {
  value: string;
  label: string;
}

/**
 * Search-as-you-type picker. The list is fetched from the server as the user types, so an item
 * past the first page of a long catalogue is as reachable as the first one. Submits the chosen id
 * under {@code name}; nothing is submitted until an option is chosen.
 */
export function LookupPicker({
  kind,
  name,
  placeholder,
  label
}: {
  kind: LookupKind;
  name: string;
  placeholder: string;
  label: string;
}) {
  const [text, setText] = useState("");
  const [chosen, setChosen] = useState<Option | null>(null);
  const [options, setOptions] = useState<Option[]>([]);
  const [open, setOpen] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const latest = useRef(0);

  useEffect(() => {
    if (!open) return;
    const ticket = ++latest.current;
    const timer = window.setTimeout(async () => {
      try {
        const response = await fetch(`/api/lookup?kind=${kind}&q=${encodeURIComponent(text)}`);
        if (!response.ok) throw new Error(String(response.status));
        const found = (await response.json()) as Option[];
        if (ticket === latest.current) {
          setOptions(found);
          setProblem(null);
        }
      } catch {
        if (ticket === latest.current) setProblem("Could not load the list. Try again.");
      }
    }, 200);
    return () => window.clearTimeout(timer);
  }, [text, open, kind]);

  return (
    <div className="relative">
      <input type="hidden" name={name} value={chosen?.value ?? ""} />
      <input
        value={chosen ? chosen.label : text}
        onChange={(event) => {
          setChosen(null);
          setText(event.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={(event) => {
          if (event.key === "Escape") setOpen(false);
        }}
        placeholder={placeholder}
        aria-label={label}
        role="combobox"
        aria-expanded={open}
        aria-autocomplete="list"
        autoComplete="off"
        className={inputClass}
      />
      {chosen ? (
        <button type="button" aria-label={`Clear ${label}`}
          onClick={() => { setChosen(null); setText(""); setOpen(true); }}
          className="absolute right-3 top-[1.15rem] text-[0.875rem] text-[var(--color-muted)]">
          Clear
        </button>
      ) : null}
      {open && !chosen ? (
        <ul role="listbox" aria-label={label}
          className="absolute z-10 mt-1 max-h-64 w-full overflow-y-auto rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] py-1 text-[0.958rem]">
          {problem ? (
            <li className="px-3 py-2 text-[var(--color-danger)]">{problem}</li>
          ) : options.length === 0 ? (
            <li className="px-3 py-2 text-[var(--color-muted)]">
              {text ? `Nothing matches "${text}".` : "Start typing to search."}
            </li>
          ) : (
            options.map((option) => (
              <li key={option.value} role="option" aria-selected={false}>
                <button type="button" className="block w-full px-3 py-2 text-left"
                  onClick={() => { setChosen(option); setOpen(false); }}>
                  {option.label}
                </button>
              </li>
            ))
          )}
        </ul>
      ) : null}
    </div>
  );
}
