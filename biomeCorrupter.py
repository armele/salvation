from __future__ import annotations

import json
import colorsys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Tuple


@dataclass(frozen=True)
class CorruptionProfile:
    """
    Target "corruption vibe" anchors (RGB 0..255).
    We'll blend original colors toward these anchors.
    """
    fog_rgb: Tuple[int, int, int] = (106, 123, 106)      # murky green-gray
    sky_rgb: Tuple[int, int, int] = (122, 133, 112)      # jaundiced gray-green
    water_rgb: Tuple[int, int, int] = (63, 107, 58)      # toxic green
    water_fog_rgb: Tuple[int, int, int] = (30, 47, 30)   # swampy dark
    grass_rgb: Tuple[int, int, int] = (126, 143, 58)     # sickly yellow-green
    foliage_rgb: Tuple[int, int, int] = (85, 107, 47)    # bruised olive


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

        # Nudge hue slightly toward green with higher corruption
        target_h = 110.0 / 360.0
        h = self._lerp_angle(h, target_h, 0.20 * strength)

        # Desaturate + darken a touch for "sick" feel
        s = max(0.0, min(1.0, s * (1.0 - 0.35 * strength)))
        v = max(0.0, min(1.0, v * (1.0 - 0.15 * strength)))

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

    biome_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("data/salvation/worldgen/biome")
    out_dir = Path(sys.argv[2]) if len(sys.argv) > 2 else None

    corruptor = BiomeCorruptor(
        biome_dir=biome_dir,
        out_dir=out_dir,
        corrupted_prefix="corrupted_",
        purified_prefix="purified_",
        namespace="salvation",
        overwrite=True,
        dry_run=False,
    )
    corruptor.run()