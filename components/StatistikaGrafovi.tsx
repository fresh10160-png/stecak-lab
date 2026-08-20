"use client";

import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from "recharts";

const BOJE = ["#4f653d", "#65804e", "#849c6d", "#a7b995", "#c9d4bd", "#3f5031"];

export function GrafPoDrzavi({
  podaci,
}: {
  podaci: { drzava: string; brojNekropola: number; brojStecaka: number }[];
}) {
  return (
    <ResponsiveContainer width="100%" height={320}>
      <BarChart data={podaci} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#e4e9de" />
        <XAxis dataKey="drzava" tick={{ fontSize: 12 }} />
        <YAxis tick={{ fontSize: 12 }} />
        <Tooltip />
        <Bar dataKey="brojStecaka" name="Broj stećaka" radius={[6, 6, 0, 0]}>
          {podaci.map((_, i) => (
            <Cell key={i} fill={BOJE[i % BOJE.length]} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

export function GrafMotiva({ podaci }: { podaci: { motiv: string; broj: number }[] }) {
  return (
    <ResponsiveContainer width="100%" height={360}>
      <BarChart
        data={podaci}
        layout="vertical"
        margin={{ top: 8, right: 16, bottom: 8, left: 24 }}
      >
        <CartesianGrid strokeDasharray="3 3" stroke="#e4e9de" />
        <XAxis type="number" tick={{ fontSize: 12 }} allowDecimals={false} />
        <YAxis
          type="category"
          dataKey="motiv"
          width={140}
          tick={{ fontSize: 12 }}
        />
        <Tooltip />
        <Bar dataKey="broj" name="Broj lokaliteta" fill="#65804e" radius={[0, 6, 6, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}
