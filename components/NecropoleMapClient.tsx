"use client";

import dynamic from "next/dynamic";
import type { Nekropola } from "@/lib/types";

const NecropoleMap = dynamic(() => import("./NecropoleMap"), {
  ssr: false,
  loading: () => (
    <div className="flex h-full min-h-[300px] w-full items-center justify-center bg-moss-50 text-sm text-stone-500">
      Učitavanje karte…
    </div>
  ),
});

export default function NecropoleMapClient(props: {
  nekropole: Nekropola[];
  visina?: string;
}) {
  return <NecropoleMap {...props} />;
}
