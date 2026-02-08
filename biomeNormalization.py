#!/usr/bin/env python3
"""
Normalize ONLY "problem" entries in a Minecraft dimension multi_noise biome list.

Reads:
  exteritio.json  (dimension file with generator.biome_source.type == minecraft:multi_noise)
  exteritio_noise_settings.json (noise settings, used to derive parameter domains from spawn_target)

Writes:
  exteritio_normalized.json (same directory by default, or via --output)

A biome entry is modified ONLY if it is deemed "problematic":
- missing/invalid parameters or missing keys
- malformed ranges
- authored in [0..1] while domain includes negatives (likely wrong domain)
- overly broad ranges (covers too much of the domain)
- values outside domain (clamped)

Usage examples:
  python normalize_exteritio.py
  python normalize_exteritio.py --input /path/exteritio.json --noise /path/exteritio_noise_settings.json
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple


CLIMATE_KEYS = ["temperature", "humidity", "continentalness", "erosion", "weirdness", "depth"]


@dataclass(frozen=True)
class Domain:
    lo: float
    hi: float

    @property
    def width(self) -> float:
        return self.hi - self.lo

    def clamp(self, x: float) -> float:
        return max(self.lo, min(self.hi, x))

    def clamp_range(self, a: float, b: float) -> Tuple[float, float]:
        lo = self.clamp(min(a, b))
        hi = self.clamp(max(a, b))
        if hi < lo:
            hi = lo
        return lo, hi


def load_json(path: str) -> Any:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def save_json(path: str, data: Any) -> None:
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def derive_domains_from_spawn_target(noise_settings: dict) -> Dict[str, Domain]:
    """
    Use noise_settings["spawn_target"] as a practical signal of the climate parameter domains.
    Falls back to vanilla-ish defaults if missing.
    """
    defaults = {
        "temperature": Domain(-1.0, 1.0),
        "humidity": Domain(-1.0, 1.0),
        "continentalness": Domain(-1.0, 1.0),
        "erosion": Domain(-1.0, 1.0),
        "weirdness": Domain(-1.0, 1.0),
        "depth": Domain(0.0, 0.0),
    }

    st = noise_settings.get("spawn_target")
    if not isinstance(st, list) or not st:
        return defaults

    mins = {k: defaults[k].lo for k in defaults}
    maxs = {k: defaults[k].hi for k in defaults}

    for entry in st:
        if not isinstance(entry, dict):
            continue
        for k in CLIMATE_KEYS:
            v = entry.get(k)
            if isinstance(v, list) and len(v) == 2 and all(isinstance(x, (int, float)) for x in v):
                a, b = float(v[0]), float(v[1])
                mins[k] = min(mins[k], a, b)
                maxs[k] = max(maxs[k], a, b)
            elif isinstance(v, (int, float)) and k == "depth":
                x = float(v)
                mins[k] = min(mins[k], x)
                maxs[k] = max(maxs[k], x)

    domains: Dict[str, Domain] = {}
    for k in defaults:
        lo, hi = float(mins[k]), float(maxs[k])
        if hi < lo:
            lo, hi = hi, lo
        domains[k] = Domain(lo, hi)
    return domains


def stable_unit_float(seed_bytes: bytes, salt: str) -> float:
    """
    Deterministic float in [0, 1) derived from seed_bytes + salt.
    """
    h = hashlib.blake2b(seed_bytes + salt.encode("utf-8"), digest_size=8).digest()
    n = int.from_bytes(h, "big")
    return (n % (10**12)) / float(10**12)


def is_range_like_0_1(a: float, b: float) -> bool:
    lo, hi = min(a, b), max(a, b)
    return lo >= -1e-6 and hi <= 1.0 + 1e-6


def extract_range(v: Any) -> Optional[Tuple[float, float]]:
    if isinstance(v, list) and len(v) == 2 and all(isinstance(x, (int, float)) for x in v):
        return float(v[0]), float(v[1])
    if isinstance(v, (int, float)):
        x = float(v)
        return x, x
    return None


def map_0_1_to_domain(a: float, b: float, d: Domain) -> Tuple[float, float]:
    """
    Interpret a,b as [0..1] and map to [d.lo..d.hi].
    """
    lo = d.lo + a * d.width
    hi = d.lo + b * d.width
    return d.clamp_range(lo, hi)


def shrink_broad_range(
    biome_name: str,
    key: str,
    domain: Domain,
    target_width_ratio: float,
    seed_bytes: bytes,
) -> Tuple[float, float]:
    """
    Create a stable niche range for this biome/key within the domain.
    """
    if domain.width <= 0:
        return domain.lo, domain.hi

    half = (target_width_ratio * domain.width) / 2.0
    margin_lo = domain.lo + half
    margin_hi = domain.hi - half
    if margin_hi < margin_lo:
        return domain.lo, domain.hi

    u = stable_unit_float(seed_bytes, f"{biome_name}:{key}")
    center = margin_lo + u * (margin_hi - margin_lo)
    return domain.clamp_range(center - half, center + half)


def normalize_biomes(
    exteritio: dict,
    noise_settings: dict,
    *,
    broad_threshold_ratio: float,
    target_width_ratio: float,
    verbose: bool,
) -> dict:
    domains = derive_domains_from_spawn_target(noise_settings)

    biome_source = exteritio.get("generator", {}).get("biome_source")
    if not isinstance(biome_source, dict) or biome_source.get("type") != "minecraft:multi_noise":
        raise ValueError("Expected exteritio.json generator.biome_source.type == 'minecraft:multi_noise'.")

    biomes = biome_source.get("biomes")
    if not isinstance(biomes, list) or not biomes:
        raise ValueError("Expected generator.biome_source.biomes to be a non-empty list.")

    changed_names: List[str] = []

    for i, entry in enumerate(biomes):
        if not isinstance(entry, dict):
            continue

        biome_name = str(entry.get("biome", f"biome_{i}"))
        seed_bytes = biome_name.encode("utf-8")

        params = entry.get("parameters")
        if not isinstance(params, dict):
            # Problem: missing/invalid parameters
            params = {}
            entry["parameters"] = params
            biome_problem = True
        else:
            biome_problem = False

        # Track per-key problems, and per-key updates staged in new_params
        new_params = dict(params)
        key_problem: Dict[str, bool] = {k: False for k in CLIMATE_KEYS}

        # 1) Ensure keys exist; missing keys => biome is problematic, but we won't touch others unless necessary.
        for k in CLIMATE_KEYS:
            if k not in params:
                key_problem[k] = True
                biome_problem = True
                # set as full domain so it can be shrunk to a niche
                d = domains.get(k, Domain(-1.0, 1.0))
                new_params[k] = [d.lo, d.hi]

        # 2) Validate, detect wrong-domain, clamp, and detect overly broad ranges.
        for k in CLIMATE_KEYS:
            d = domains.get(k, Domain(-1.0, 1.0))
            r = extract_range(new_params.get(k))

            if r is None:
                key_problem[k] = True
                biome_problem = True
                new_params[k] = [d.lo, d.hi]
                r = (d.lo, d.hi)

            a, b = float(r[0]), float(r[1])

            # Wrong-domain hint: domain includes negatives but authored in [0..1].
            if d.lo < 0.0 and is_range_like_0_1(a, b):
                # Only treat as a "problem" if it's not already a narrow special case.
                # If it's broad-ish in [0..1], it's almost certainly accidental global coverage.
                if (max(a, b) - min(a, b)) >= 0.50:
                    key_problem[k] = True
                    biome_problem = True
                    a, b = map_0_1_to_domain(a, b, d)

            # Out-of-domain => problem and clamp
            if a < d.lo - 1e-6 or b > d.hi + 1e-6:
                key_problem[k] = True
                biome_problem = True

            a, b = d.clamp_range(a, b)

            # Overly broad => problem and shrink
            width = b - a
            if d.width > 0 and width >= broad_threshold_ratio * d.width:
                key_problem[k] = True
                biome_problem = True
                a, b = shrink_broad_range(biome_name, k, d, target_width_ratio, seed_bytes)

            new_params[k] = [round(a, 6), round(b, 6)]

        # 3) If the biome is not a problem, leave it exactly as it was.
        if not biome_problem:
            continue

        # 4) For problem biomes, also ensure offset exists (but don't override if valid).
        off = new_params.get("offset", params.get("offset"))
        if not isinstance(off, (int, float)):
            new_params["offset"] = 0.0

        entry["parameters"] = new_params
        changed_names.append(biome_name)

    exteritio["generator"]["biome_source"]["biomes"] = biomes

    if verbose:
        if changed_names:
            print("Normalized problem biome entries:")
            for name in changed_names:
                print(f"  - {name}")
        else:
            print("No problem entries detected; no changes made.")

    return exteritio


def resolve_default_paths(script_path: str) -> Tuple[str, str, str]:
    here = os.path.dirname(os.path.abspath(script_path))
    return (
        os.path.join(here, "exteritio.json"),
        os.path.join(here, "exteritio_noise_settings.json"),
        os.path.join(here, "exteritio_normalized.json"),
    )


def main() -> None:
    default_in, default_noise, default_out = resolve_default_paths(__file__)

    p = argparse.ArgumentParser(description="Normalize only problem entries in exteritio multi_noise biome parameters.")
    p.add_argument("--input", default=default_in, help="Path to exteritio.json (default: alongside this script).")
    p.add_argument("--noise", default=default_noise, help="Path to exteritio_noise_settings.json (default: alongside this script).")
    p.add_argument("--output", default=default_out, help="Path to write exteritio_normalized.json (default: alongside this script).")
    p.add_argument("--broad-threshold", type=float, default=0.92,
                   help="If a range covers >= this fraction of the domain, it is considered overly broad (default: 0.92).")
    p.add_argument("--target-width", type=float, default=0.30,
                   help="When shrinking broad ranges, new width as fraction of domain (default: 0.30).")
    p.add_argument("--verbose", action="store_true", help="Print which biomes were normalized.")
    args = p.parse_args()

    exteritio = load_json(args.input)
    noise_settings = load_json(args.noise)

    normalized = normalize_biomes(
        exteritio,
        noise_settings,
        broad_threshold_ratio=args.broad_threshold,
        target_width_ratio=args.target_width,
        verbose=args.verbose,
    )

    save_json(args.output, normalized)
    print(f"Wrote: {args.output}")


if __name__ == "__main__":
    main()