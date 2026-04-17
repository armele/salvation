from __future__ import annotations

import argparse
import colorsys
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Iterable, Tuple

DEFAULT_CORRUPTION_PROFILE_PATH = Path("src/main/resources/data/salvation/corruption_profiles/bruised_palette.json")


@dataclass(frozen=True)
class CorruptionProfile:
    """
    Target "corruption vibe" anchors (RGB 0..255).
    We'll blend original colors toward these anchors.
    """
    fog_rgb: Tuple[int, int, int] = (112, 88, 98)        # muted bruise haze
    sky_rgb: Tuple[int, int, int] = (136, 110, 124)      # dusty mauve sky
    water_rgb: Tuple[int, int, int] = (96, 70, 86)       # tainted plum water
    water_fog_rgb: Tuple[int, int, int] = (47, 33, 42)   # deep bruise shadow
    grass_rgb: Tuple[int, int, int] = (129, 103, 76)     # withered brown-mauve
    foliage_rgb: Tuple[int, int, int] = (102, 76, 68)    # bruised bark-olive
    hue_target: float = 335.0 / 360.0
    hue_shift_strength: float = 0.16
    saturation_reduction: float = 0.22
    value_reduction: float = 0.18

    @classmethod
    def from_json_file(cls, path: str | Path) -> "CorruptionProfile":
        profile_path = Path(path)
        with profile_path.open("r", encoding="utf-8") as f:
            payload = json.load(f)

        if not isinstance(payload, dict):
            raise ValueError(f"Corruption profile must be a JSON object: {profile_path}")

        anchors = payload.get("anchors", {})
        hsv = payload.get("hsv", {})
        if not isinstance(anchors, dict) or not isinstance(hsv, dict):
            raise ValueError(f"Corruption profile must contain object-valued anchors and hsv sections: {profile_path}")

        defaults = cls()
        return cls(
            fog_rgb=cls._parse_rgb_color(anchors, "fog", defaults.fog_rgb),
            sky_rgb=cls._parse_rgb_color(anchors, "sky", defaults.sky_rgb),
            water_rgb=cls._parse_rgb_color(anchors, "water", defaults.water_rgb),
            water_fog_rgb=cls._parse_rgb_color(anchors, "water_fog", defaults.water_fog_rgb),
            grass_rgb=cls._parse_rgb_color(anchors, "grass", defaults.grass_rgb),
            foliage_rgb=cls._parse_rgb_color(anchors, "foliage", defaults.foliage_rgb),
            hue_target=cls._parse_float(hsv, "hue_target", defaults.hue_target),
            hue_shift_strength=cls._parse_float(hsv, "hue_shift_strength", defaults.hue_shift_strength),
            saturation_reduction=cls._parse_float(hsv, "saturation_reduction", defaults.saturation_reduction),
            value_reduction=cls._parse_float(hsv, "value_reduction", defaults.value_reduction),
        )

    @staticmethod
    def _parse_rgb_color(section: Dict[str, Any], key: str, fallback: Tuple[int, int, int]) -> Tuple[int, int, int]:
        raw = section.get(key)
        if not isinstance(raw, int):
            return fallback
        return ((raw >> 16) & 0xFF, (raw >> 8) & 0xFF, raw & 0xFF)

    @staticmethod
    def _parse_float(section: Dict[str, Any], key: str, fallback: float) -> float:
        raw = section.get(key)
        if isinstance(raw, (int, float)):
            return float(raw)
        return fallback


class BiomeCorruptor:
    """
    Reads biome JSONs and emits:
      - corrupted_<name>.json (mutated colors + spawn rule)
      - purified_<name>.json (unmodified copy)

    Skips inputs that already start with:
      - corrupted_
      - corruption_ (older naming)
      - purified_

    Spawn rule:
      mob_spawn_settings.spawners.<category>[].type:
        "minecraft:creature" -> "salvation:corrupted_creature"

      Patched categories:
        - creature
        - water_creature (and water_creatures if present)

    Also emits:
      - biome_definition.json in the current working directory with only
        newly-created corrupted biomes, suitable for dropping into a
        minecraft:multi_noise biome_source definition.
    """

    def __init__(
        self,
        biome_dir: str | Path,
        out_dir: str | Path | None = None,
        corrupted_prefix: str = "corrupted_",
        purified_prefix: str = "purified_",
        profile: CorruptionProfile | None = None,
        namespace: str = "salvation",
        overwrite: bool = False,
        dry_run: bool = False,
    ) -> None:
        self.biome_dir = Path(biome_dir)
        self.out_dir = Path(out_dir) if out_dir else self.biome_dir
        self.corrupted_prefix = corrupted_prefix
        self.purified_prefix = purified_prefix
        self.profile = profile or CorruptionProfile()
        self.namespace = namespace
        self.overwrite = overwrite
        self.dry_run = dry_run

        # Collect newly created corrupted biome IDs for biome_definition.json
        self.created_corrupted_biomes: list[str] = []
        # Output file name written in the current working directory
        self.definition_filename: str = "biome_definition.json"

        if not self.biome_dir.exists():
            raise FileNotFoundError(f"Biome directory not found: {self.biome_dir}")
        self.out_dir.mkdir(parents=True, exist_ok=True)

    # ---------------- Public API ----------------

    def run(self) -> None:
        print(f"[STEP] Clearing created corrupted biomes.")
        self.created_corrupted_biomes.clear()


        print(f"[STEP] Iterating biome directory.")
        for path in sorted(self.biome_dir.glob("*.json")):
            if self._should_skip(path):
                continue

            data = self._load_json(path)
            if not isinstance(data, dict):
                print(f"[SKIP] Not an object JSON: {path.name}")
                continue

            base_name = path.stem  # e.g. "bamboo_jungle"
            purified_name = f"{self.purified_prefix}{base_name}.json"
            corrupted_name = f"{self.corrupted_prefix}{base_name}.json"

            purified_path = self.out_dir / purified_name
            corrupted_path = self.out_dir / corrupted_name

            # 1) purified is an unmodified copy of the original
            purified_written = self._write_json_if_needed(purified_path, data, label="purified")

            # 2) corrupted is mutated
            corrupted = self._corrupt_biome_dict(data, source_name=base_name)
            self._apply_spawn_rule(corrupted)

            corrupted_written = self._write_json_if_needed(corrupted_path, corrupted, label="corrupted")

            # Record only if we actually wrote the corrupted file (new or overwritten)
            if corrupted_written:
                biome_id = f"{self.namespace}:{self.corrupted_prefix}{base_name}"
                self.created_corrupted_biomes.append(biome_id)

        print(f"[STEP] Writing biome definition file.")
        # 3) Emit biome_definition.json in the current working directory
        self._write_biome_definition_file()

    def refresh_corrupted_colors_from_mappings(
        self,
        mapping_path: str | Path,
        source_biome_dir: str | Path,
        target_biome_dir: str | Path | None = None,
    ) -> None:
        mapping_path = Path(mapping_path)
        source_root = Path(source_biome_dir)
        target_root = Path(target_biome_dir) if target_biome_dir else self.out_dir

        if not mapping_path.exists():
            raise FileNotFoundError(f"Mapping file not found: {mapping_path}")
        if not source_root.exists():
            raise FileNotFoundError(f"Source biome directory not found: {source_root}")
        if not target_root.exists():
            raise FileNotFoundError(f"Target biome directory not found: {target_root}")

        payload = self._load_json(mapping_path)
        mappings = payload.get("mappings", []) if isinstance(payload, dict) else []
        if not isinstance(mappings, list):
            raise ValueError(f"Mapping file has invalid 'mappings' array: {mapping_path}")

        updated = 0
        skipped = 0

        print(f"[STEP] Refreshing corrupted biome colors from mappings: {mapping_path}")
        for entry in mappings:
            if not isinstance(entry, dict):
                skipped += 1
                continue

            vanilla_id = entry.get("vanilla")
            corrupted_id = entry.get("corrupted")
            if not isinstance(vanilla_id, str) or not isinstance(corrupted_id, str):
                skipped += 1
                continue

            source_path = self._resolve_biome_json_path(source_root, vanilla_id)
            target_path = self._resolve_biome_json_path(target_root, corrupted_id)

            if source_path is None:
                print(f"[SKIP] Missing source biome for {vanilla_id}")
                skipped += 1
                continue

            if target_path is None:
                print(f"[SKIP] Missing corrupted biome for {corrupted_id}")
                skipped += 1
                continue

            source_biome = self._load_json(source_path)
            target_biome = self._load_json(target_path)
            if not isinstance(source_biome, dict) or not isinstance(target_biome, dict):
                print(f"[SKIP] Non-object biome JSON for mapping {vanilla_id} -> {corrupted_id}")
                skipped += 1
                continue

            recolored = self._apply_rederived_palette(
                target_biome=target_biome,
                source_biome=source_biome,
                source_name=vanilla_id.split(":", 1)[-1],
            )

            if self._write_json_if_needed(target_path, recolored, label="recolored corrupted biome"):
                updated += 1

        print(f"[OK] Refreshed {updated} corrupted biome palettes; skipped {skipped}.")

    # ---------------- Mutations ----------------

    def _corrupt_biome_dict(self, biome: Dict[str, Any], source_name: str) -> Dict[str, Any]:
        # deep copy via JSON round-trip (safe for plain dict/list/int/float/str/bool)
        out: Dict[str, Any] = json.loads(json.dumps(biome))

        effects = out.get("effects")
        if not isinstance(effects, dict):
            effects = {}
            out["effects"] = effects

        strengths = self._strengths_for_biome_name(source_name)

        self._corrupt_effect_color(effects, "fog_color", self.profile.fog_rgb, strengths["fog"], add_missing=True)
        self._corrupt_effect_color(effects, "sky_color", self.profile.sky_rgb, strengths["sky"], add_missing=True)
        self._corrupt_effect_color(effects, "water_color", self.profile.water_rgb, strengths["water"], add_missing=True)
        self._corrupt_effect_color(effects, "water_fog_color", self.profile.water_fog_rgb, strengths["water_fog"], add_missing=True)

        # These often aren't present; adding them helps sell the vibe.
        self._corrupt_effect_color(effects, "grass_color", self.profile.grass_rgb, strengths["grass"], add_missing=True)
        self._corrupt_effect_color(effects, "foliage_color", self.profile.foliage_rgb, strengths["foliage"], add_missing=True)

        return out

    def _apply_rederived_palette(
        self,
        target_biome: Dict[str, Any],
        source_biome: Dict[str, Any],
        source_name: str,
    ) -> Dict[str, Any]:
        recolored_target: Dict[str, Any] = json.loads(json.dumps(target_biome))
        recolored_source = self._corrupt_biome_dict(source_biome, source_name=source_name)

        source_effects = recolored_source.get("effects")
        if not isinstance(source_effects, dict):
            return recolored_target

        target_effects = recolored_target.get("effects")
        if not isinstance(target_effects, dict):
            target_effects = {}
            recolored_target["effects"] = target_effects

        for key in ("fog_color", "sky_color", "water_color", "water_fog_color", "grass_color", "foliage_color"):
            if key in source_effects:
                target_effects[key] = source_effects[key]

        return recolored_target

    def _load_corrupted_entity_tag(self) -> set[str]:
        """
        Loads data/salvation/tags/entity_type/corrupted_entity.json
        Returns a set of fully-qualified entity IDs (e.g. minecraft:pig)
        """
        tag_path = (
            Path("data")
            / self.namespace
            / "tags"
            / "entity_type"
            / "corruptable_entity.json"
        )

        if not tag_path.exists():
            print(f"[WARN] Corrupted entity tag not found: {tag_path}")
            return set()

        try:
            with tag_path.open("r", encoding="utf-8") as f:
                data = json.load(f)
        except Exception as e:
            print(f"[WARN] Failed to read corrupted_entity tag: {e}")
            return set()

        values = data.get("values", [])
        return {v for v in values if isinstance(v, str)}

    def _apply_spawn_rule(self, biome: Dict[str, Any]) -> None:
        """
        In mob_spawn_settings.spawners.<category>[].type:

        If entity is listed in tag:
            salvation:corrupted_entity
        then:
            minecraft:<entity> -> salvation:corrupted_<entity>

        Patched categories:
        - creature
        - water_creature
        - water_creatures (defensive)
        """
        mss = biome.get("mob_spawn_settings")
        if not isinstance(mss, dict):
            return

        spawners = mss.get("spawners")
        if not isinstance(spawners, dict):
            return

        corruptable_entities = self._load_corrupted_entity_tag()
        if not corruptable_entities:
            return

        for category in ("creature", "water_creature", "water_creatures"):
            entries = spawners.get(category)
            if not isinstance(entries, list):
                continue

            for entry in entries:
                if not isinstance(entry, dict):
                    continue

                entity_id = entry.get("type")
                if not isinstance(entity_id, str):
                    continue

                # Only corrupt entities explicitly tagged
                if entity_id not in corruptable_entities:
                    continue

                namespace, path = entity_id.split(":", 1)

                # Avoid double-corruption
                if namespace == self.namespace and path.startswith("corrupted_"):
                    continue

                entry["type"] = f"{self.namespace}:corrupted_{path}"

    # ---------------- Biome definition output ----------------

    def _write_biome_definition_file(self) -> None:
        out_path = Path.cwd() / self.definition_filename

        print(f"[STEP] Found {len(self.created_corrupted_biomes)} corrupted biomes.")

        payload = {
            "biomes": [
                {
                    "biome": biome_id,
                    "parameters": self._default_parameters_for_biome_id(biome_id),
                }
                for biome_id in self.created_corrupted_biomes
            ]
        }

        if self.dry_run:
            print(f"[DRY] Would write biome definition: {out_path}")
            print(f"[DRY] biomes included: {len(self.created_corrupted_biomes)}")
            return

        with out_path.open("w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2, sort_keys=False)
            f.write("\n")

        print(f"[OK] Wrote biome definition: {out_path} ({len(self.created_corrupted_biomes)} biomes)")

    def _default_parameters_for_biome_id(self, biome_id: str) -> Dict[str, Any]:
        """
        Heuristic defaults. Biome JSON files do NOT contain these parameters,
        so this generates a starter mapping you can tune later.

        Returned format matches minecraft:multi_noise:
          temperature/humidity/continentalness/erosion/weirdness/depth + offset
        """
        name = biome_id.split(":", 1)[-1].lower()  # e.g. "corrupted_badlands"

        # Strip common prefixes to get the "base biome name"
        for pfx in (self.corrupted_prefix.lower(), "corruption_", "corrupted_"):
            if name.startswith(pfx):
                name = name[len(pfx):]
                break

        # Baseline: broad coverage
        params: Dict[str, Any] = {
            "temperature": [0.0, 1.0],
            "humidity": [0.0, 1.0],
            "continentalness": [0.0, 1.0],
            "erosion": [0.0, 1.0],
            "weirdness": [-1.0, 1.0],
            "depth": [0.0, 0.0],
            "offset": 0.0,
        }

        # Hot/dry family (badlands/desert/savanna)
        if any(k in name for k in ["badlands", "mesa", "desert", "savanna"]):
            params["temperature"] = [0.7, 1.0]
            params["humidity"] = [0.0, 0.35]
            params["continentalness"] = [0.2, 1.0]
            params["erosion"] = [0.0, 0.7]

        # Jungle / bamboo / lush / swamp family
        if any(k in name for k in ["jungle", "bamboo", "lush", "swamp", "mangrove"]):
            params["temperature"] = [0.75, 1.0]
            params["humidity"] = [0.6, 1.0]
            params["continentalness"] = [0.0, 1.0]
            params["erosion"] = [0.0, 1.0]

        # Temperate plains/forest
        if any(k in name for k in ["plains", "forest", "birch", "meadow"]):
            params["temperature"] = [0.4, 0.8]
            params["humidity"] = [0.3, 0.8]
            params["continentalness"] = [0.0, 1.0]
            params["erosion"] = [0.0, 1.0]

        # Cold family (taiga/snow/ice)
        if any(k in name for k in ["taiga", "snow", "frozen", "ice", "grove"]):
            params["temperature"] = [0.0, 0.35]
            params["humidity"] = [0.2, 1.0]
            params["continentalness"] = [0.0, 1.0]
            params["erosion"] = [0.0, 1.0]

        return params

    # ---------------- Heuristics ----------------

    def _strengths_for_biome_name(self, name: str) -> Dict[str, float]:
        n = name.lower()
        base = {
            "fog": 0.55,
            "sky": 0.45,
            "water": 0.70,
            "water_fog": 0.70,
            "grass": 0.60,
            "foliage": 0.60,
        }

        if any(k in n for k in ["badlands", "desert", "savanna", "mesa"]):
            base["fog"] = 0.60
            base["sky"] = 0.55
            base["grass"] = 0.40
            base["foliage"] = 0.40
            base["water"] = 0.75
            base["water_fog"] = 0.75

        if any(k in n for k in ["jungle", "bamboo", "lush", "swamp", "mangrove"]):
            base["fog"] = 0.55
            base["sky"] = 0.40
            base["grass"] = 0.75
            base["foliage"] = 0.75
            base["water"] = 0.75
            base["water_fog"] = 0.80

        if any(k in n for k in ["snow", "frozen", "ice", "grove", "taiga"]):
            base["fog"] = 0.60
            base["sky"] = 0.50
            base["grass"] = 0.50
            base["foliage"] = 0.50
            base["water"] = 0.65
            base["water_fog"] = 0.70

        return base

    # ---------------- Color helpers (decimal int output) ----------------

    def _corrupt_effect_color(
        self,
        effects: Dict[str, Any],
        key: str,
        target_rgb: Tuple[int, int, int],
        strength: float,
        add_missing: bool,
    ) -> None:
        current = effects.get(key)
        if isinstance(current, int):
            src_rgb = self._int_to_rgb(current)
            new_rgb = self._corrupt_rgb(src_rgb, target_rgb, strength)
            effects[key] = self._rgb_to_int(new_rgb)
        else:
            if add_missing:
                effects[key] = self._rgb_to_int(target_rgb)

    def _corrupt_rgb(
        self,
        src_rgb: Tuple[int, int, int],
        target_rgb: Tuple[int, int, int],
        strength: float,
    ) -> Tuple[int, int, int]:
        strength = max(0.0, min(1.0, strength))

        blended = self._lerp_rgb(src_rgb, target_rgb, strength)

        r, g, b = (c / 255.0 for c in blended)
        h, s, v = colorsys.rgb_to_hsv(r, g, b)

        # Nudge hue toward a dusty purple family while preserving biome variation.
        target_h = self.profile.hue_target
        h = self._lerp_angle(h, target_h, self.profile.hue_shift_strength * strength)

        # Slightly mute and darken toward a bruised look without flattening every biome.
        s = max(0.0, min(1.0, s * (1.0 - self.profile.saturation_reduction * strength)))
        v = max(0.0, min(1.0, v * (1.0 - self.profile.value_reduction * strength)))

        rr, gg, bb = colorsys.hsv_to_rgb(h, s, v)
        return (int(round(rr * 255)), int(round(gg * 255)), int(round(bb * 255)))

    @staticmethod
    def _lerp_rgb(a: Tuple[int, int, int], b: Tuple[int, int, int], t: float) -> Tuple[int, int, int]:
        return (
            int(round(a[0] + (b[0] - a[0]) * t)),
            int(round(a[1] + (b[1] - a[1]) * t)),
            int(round(a[2] + (b[2] - a[2]) * t)),
        )

    @staticmethod
    def _lerp_angle(a: float, b: float, t: float) -> float:
        d = (b - a + 0.5) % 1.0 - 0.5
        return (a + d * t) % 1.0

    @staticmethod
    def _int_to_rgb(color: int) -> Tuple[int, int, int]:
        r = (color >> 16) & 0xFF
        g = (color >> 8) & 0xFF
        b = color & 0xFF
        return (r, g, b)

    @staticmethod
    def _rgb_to_int(rgb: Tuple[int, int, int]) -> int:
        r, g, b = rgb
        return (r << 16) | (g << 8) | b

    # ---------------- IO helpers ----------------

    def _should_skip(self, path: Path) -> bool:
        stem = path.stem.lower()
        if stem.startswith("purified_"):
            print(f"[SKIP] Already purified: {path.name}")
            return True
        if stem.startswith("corrupted_"):
            print(f"[SKIP] Already corrupted: {path.name}")
            return True
        if stem.startswith("corruption_"):
            print(f"[SKIP] Already corruption_* (legacy): {path.name}")
            return True
        return False

    def _write_json_if_needed(self, path: Path, data: Any, label: str) -> bool:
        """
        Returns True if we wrote the file (new or overwritten), False otherwise.
        """
        if path.exists() and not self.overwrite:
            print(f"[SKIP] Exists (overwrite=False): {path.name}")
            return False

        if self.dry_run:
            print(f"[DRY] Would write {label}: {path.name}")
            return True

        with path.open("w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2, sort_keys=True)
            f.write("\n")

        print(f"[OK] Wrote {label}: {path.name}")
        return True

    @staticmethod
    def _load_json(path: Path) -> Any:
        with path.open("r", encoding="utf-8") as f:
            return json.load(f)

    @staticmethod
    def _split_resource_location(resource_id: str) -> Tuple[str, str]:
        if ":" in resource_id:
            return tuple(resource_id.split(":", 1))  # type: ignore[return-value]
        return ("minecraft", resource_id)

    def _resolve_biome_json_path(self, root: Path, resource_id: str) -> Path | None:
        namespace, path = self._split_resource_location(resource_id)
        candidates = list(self._candidate_biome_paths(root, namespace, path))
        for candidate in candidates:
            if candidate.exists():
                return candidate
        return None

    @staticmethod
    def _candidate_biome_paths(root: Path, namespace: str, biome_path: str) -> Iterable[Path]:
        filename = Path(*biome_path.split("/")).with_suffix(".json")
        flat_name = biome_path.replace("/", "_") + ".json"
        yield root / filename
        yield root / flat_name
        yield root / "data" / namespace / "worldgen" / "biome" / filename
        yield root / namespace / "worldgen" / "biome" / filename


if __name__ == "__main__":
    """
    Example usage:

    # 1) Create corrupted_*.json + purified_*.json in place:
    python biome_corruptor.py data/salvation/worldgen/biome

    # 2) Write outputs to a separate folder:
    python biome_corruptor.py data/salvation/worldgen/biome data/salvation/worldgen/biome_out

    # It will also create biome_definition.json in your current working directory.
    """
    import sys

    argv = sys.argv[1:]
    if argv and argv[0] not in {"generate", "refresh-colors", "-h", "--help"}:
        argv = ["generate", *argv]

    parser = argparse.ArgumentParser(description="Generate or recolor Salvation biome JSONs.")
    subparsers = parser.add_subparsers(dest="command")

    generate_parser = subparsers.add_parser("generate", help="Generate corrupted_*/purified_* biomes from source biome JSONs.")
    generate_parser.add_argument(
        "biome_dir",
        nargs="?",
        default="src/main/resources/data/salvation/worldgen/biome",
        help="Directory containing source biome JSONs.",
    )
    generate_parser.add_argument(
        "out_dir",
        nargs="?",
        default=None,
        help="Output directory for generated biome JSONs.",
    )
    generate_parser.add_argument("--dry-run", action="store_true", help="Show what would be written without changing files.")
    generate_parser.add_argument(
        "--profile",
        default=str(DEFAULT_CORRUPTION_PROFILE_PATH),
        help="Path to the corruption profile JSON.",
    )

    refresh_parser = subparsers.add_parser(
        "refresh-colors",
        help="Update existing corrupted biome colors from source vanilla biomes using a biome mapping file.",
    )
    refresh_parser.add_argument(
        "--mappings",
        default="src/main/resources/data/salvation/salvation_biome_mappings/default.json",
        help="Path to the biome mapping JSON.",
    )
    refresh_parser.add_argument(
        "--source-biome-dir",
        required=True,
        help="Directory containing source vanilla biome JSONs.",
    )
    refresh_parser.add_argument(
        "--target-biome-dir",
        default="src/main/resources/data/salvation/worldgen/biome",
        help="Directory containing the corrupted biome JSONs to refresh.",
    )
    refresh_parser.add_argument("--dry-run", action="store_true", help="Show what would be written without changing files.")
    refresh_parser.add_argument(
        "--profile",
        default=str(DEFAULT_CORRUPTION_PROFILE_PATH),
        help="Path to the corruption profile JSON.",
    )

    args = parser.parse_args(argv)
    command = args.command or "generate"
    profile = CorruptionProfile.from_json_file(args.profile)

    if command == "generate":
        corruptor = BiomeCorruptor(
            biome_dir=Path(args.biome_dir),
            out_dir=Path(args.out_dir) if args.out_dir else None,
            corrupted_prefix="corrupted_",
            purified_prefix="purified_",
            profile=profile,
            namespace="salvation",
            overwrite=True,
            dry_run=args.dry_run,
        )
        corruptor.run()
    elif command == "refresh-colors":
        target_biome_dir = Path(args.target_biome_dir)
        corruptor = BiomeCorruptor(
            biome_dir=target_biome_dir,
            out_dir=target_biome_dir,
            corrupted_prefix="corrupted_",
            purified_prefix="purified_",
            profile=profile,
            namespace="salvation",
            overwrite=True,
            dry_run=args.dry_run,
        )
        corruptor.refresh_corrupted_colors_from_mappings(
            mapping_path=Path(args.mappings),
            source_biome_dir=Path(args.source_biome_dir),
            target_biome_dir=target_biome_dir,
        )
    else:
        parser.print_help()
