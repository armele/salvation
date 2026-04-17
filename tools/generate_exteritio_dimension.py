import argparse
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAPPINGS_PATH = ROOT / "src/main/resources/data/salvation/salvation_biome_mappings/default.json"
OUTPUT_PATH = ROOT / "src/main/resources/data/salvation/dimension/exteritio.json"


def span(a, b):
    if isinstance(a, tuple):
        a = a[0]
    if isinstance(b, tuple):
        b = b[1]
    if a > b:
        raise ValueError(f"bad span: {a} > {b}")
    return (float(a), float(b))


def to_range(param):
    return [round(param[0], 6), round(param[1], 6)]


def param_max(param):
    return param[1]


FULL = (-1.0, 1.0)
TEMPS = [(-1.0, -0.45), (-0.45, -0.15), (-0.15, 0.2), (0.2, 0.55), (0.55, 1.0)]
HUMIDITIES = [(-1.0, -0.35), (-0.35, -0.1), (-0.1, 0.1), (0.1, 0.3), (0.3, 1.0)]
EROSIONS = [(-1.0, -0.78), (-0.78, -0.375), (-0.375, -0.2225), (-0.2225, 0.05), (0.05, 0.45), (0.45, 0.55), (0.55, 1.0)]
FROZEN_RANGE = TEMPS[0]
UNFROZEN_RANGE = span(TEMPS[1], TEMPS[4])

MUSHROOM = (-1.2, -1.05)
DEEP_OCEAN = (-1.05, -0.455)
OCEAN = (-0.455, -0.19)
COAST = (-0.19, -0.11)
INLAND = (-0.11, 0.55)
NEAR_INLAND = (-0.11, 0.03)
MID_INLAND = (0.03, 0.3)
FAR_INLAND = (0.3, 1.0)

OCEANS = [
    ["minecraft:deep_frozen_ocean", "minecraft:deep_cold_ocean", "minecraft:deep_ocean", "minecraft:deep_lukewarm_ocean", "minecraft:warm_ocean"],
    ["minecraft:frozen_ocean", "minecraft:cold_ocean", "minecraft:ocean", "minecraft:lukewarm_ocean", "minecraft:warm_ocean"],
]
MIDDLE_BIOMES = [
    ["minecraft:snowy_plains", "minecraft:snowy_plains", "minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:taiga"],
    ["minecraft:plains", "minecraft:plains", "minecraft:forest", "minecraft:taiga", "minecraft:old_growth_spruce_taiga"],
    ["minecraft:flower_forest", "minecraft:plains", "minecraft:forest", "minecraft:birch_forest", "minecraft:dark_forest"],
    ["minecraft:savanna", "minecraft:savanna", "minecraft:forest", "minecraft:jungle", "minecraft:jungle"],
    ["minecraft:desert", "minecraft:desert", "minecraft:desert", "minecraft:desert", "minecraft:desert"],
]
MIDDLE_BIOMES_VARIANT = [
    ["minecraft:ice_spikes", None, "minecraft:snowy_taiga", None, None],
    [None, None, None, None, "minecraft:old_growth_pine_taiga"],
    ["minecraft:sunflower_plains", None, None, "minecraft:old_growth_birch_forest", None],
    [None, None, "minecraft:plains", "minecraft:sparse_jungle", "minecraft:bamboo_jungle"],
    [None, None, None, None, None],
]
PLATEAU_BIOMES = [
    ["minecraft:snowy_plains", "minecraft:snowy_plains", "minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:snowy_taiga"],
    ["minecraft:meadow", "minecraft:meadow", "minecraft:forest", "minecraft:taiga", "minecraft:old_growth_spruce_taiga"],
    ["minecraft:meadow", "minecraft:meadow", "minecraft:meadow", "minecraft:meadow", "minecraft:dark_forest"],
    ["minecraft:savanna_plateau", "minecraft:savanna_plateau", "minecraft:forest", "minecraft:forest", "minecraft:jungle"],
    ["minecraft:badlands", "minecraft:badlands", "minecraft:badlands", "minecraft:wooded_badlands", "minecraft:wooded_badlands"],
]
PLATEAU_BIOMES_VARIANT = [
    ["minecraft:ice_spikes", None, None, None, None],
    ["minecraft:cherry_grove", None, "minecraft:meadow", "minecraft:meadow", "minecraft:old_growth_pine_taiga"],
    ["minecraft:cherry_grove", "minecraft:cherry_grove", "minecraft:forest", "minecraft:birch_forest", None],
    [None, None, None, None, None],
    ["minecraft:eroded_badlands", "minecraft:eroded_badlands", None, None, None],
]
SHATTERED_BIOMES = [
    ["minecraft:windswept_gravelly_hills", "minecraft:windswept_gravelly_hills", "minecraft:windswept_hills", "minecraft:windswept_forest", "minecraft:windswept_forest"],
    ["minecraft:windswept_gravelly_hills", "minecraft:windswept_gravelly_hills", "minecraft:windswept_hills", "minecraft:windswept_forest", "minecraft:windswept_forest"],
    ["minecraft:windswept_hills", "minecraft:windswept_hills", "minecraft:windswept_hills", "minecraft:windswept_forest", "minecraft:windswept_forest"],
    [None, None, None, None, None],
    [None, None, None, None, None],
]


def load_mapping():
    data = json.loads(MAPPINGS_PATH.read_text(encoding="utf-8"))
    return {entry["vanilla"]: entry["corrupted"] for entry in data["mappings"]}


def pick_middle_biome(temperature, humidity, weirdness):
    if param_max(weirdness) < 0:
        return MIDDLE_BIOMES[temperature][humidity]
    variant = MIDDLE_BIOMES_VARIANT[temperature][humidity]
    return variant or MIDDLE_BIOMES[temperature][humidity]


def pick_badlands_biome(humidity, weirdness):
    if humidity < 2:
        return "minecraft:badlands" if param_max(weirdness) < 0 else "minecraft:eroded_badlands"
    return "minecraft:badlands" if humidity < 3 else "minecraft:wooded_badlands"


def pick_middle_or_badlands_if_hot(temperature, humidity, weirdness):
    return pick_badlands_biome(humidity, weirdness) if temperature == 4 else pick_middle_biome(temperature, humidity, weirdness)


def pick_plateau_biome(temperature, humidity, weirdness):
    if param_max(weirdness) >= 0:
        variant = PLATEAU_BIOMES_VARIANT[temperature][humidity]
        if variant is not None:
            return variant
    return PLATEAU_BIOMES[temperature][humidity]


def pick_slope_biome(temperature, humidity, weirdness):
    if temperature >= 3:
        return pick_plateau_biome(temperature, humidity, weirdness)
    return "minecraft:snowy_slopes" if humidity <= 1 else "minecraft:grove"


def pick_middle_or_badlands_if_hot_or_slope_if_cold(temperature, humidity, weirdness):
    return pick_slope_biome(temperature, humidity, weirdness) if temperature == 0 else pick_middle_or_badlands_if_hot(temperature, humidity, weirdness)


def pick_shattered_biome(temperature, humidity, weirdness):
    shattered = SHATTERED_BIOMES[temperature][humidity]
    return pick_middle_biome(temperature, humidity, weirdness) if shattered is None else shattered


def maybe_pick_windswept_savanna_biome(temperature, humidity, weirdness, key):
    return "minecraft:windswept_savanna" if temperature > 1 and humidity < 4 and param_max(weirdness) >= 0 else key


def pick_peak_biome(temperature, humidity, weirdness):
    if temperature <= 2:
        return "minecraft:jagged_peaks" if param_max(weirdness) < 0 else "minecraft:frozen_peaks"
    return "minecraft:stony_peaks" if temperature == 3 else pick_badlands_biome(humidity, weirdness)


def pick_beach_biome(temperature, humidity):
    if temperature == 0:
        return "minecraft:snowy_beach"
    return "minecraft:desert" if temperature == 4 else "minecraft:beach"


def pick_shattered_coast_biome(temperature, humidity, weirdness):
    base = pick_middle_biome(temperature, humidity, weirdness) if param_max(weirdness) >= 0 else pick_beach_biome(temperature, humidity)
    return maybe_pick_windswept_savanna_biome(temperature, humidity, weirdness, base)


class Builder:
    def __init__(self, remap, inject_exotics=False):
        self.remap = remap
        self.inject_exotics = inject_exotics
        self.entries = []
        self.seen = set()

    def remap_exotic_biome(self, biome, temperature, humidity, continentalness, erosion, depth, weirdness):
        if not self.inject_exotics:
            return biome

        depth_min, depth_max = depth
        cont_min, cont_max = continentalness
        erosion_min, erosion_max = erosion
        weird_min, weird_max = weirdness

        # Keep ocean, coast, valleys, and underground selections stable. Exotic biomes
        # are a sparse inland overlay, not a replacement climate map.
        if depth_max >= 0.2:
            return biome

        if cont_max < 0.3:
            return biome

        if biome in {
            "minecraft:mushroom_fields",
            "minecraft:deep_frozen_ocean",
            "minecraft:deep_cold_ocean",
            "minecraft:deep_ocean",
            "minecraft:deep_lukewarm_ocean",
            "minecraft:frozen_ocean",
            "minecraft:cold_ocean",
            "minecraft:ocean",
            "minecraft:lukewarm_ocean",
            "minecraft:warm_ocean",
            "minecraft:stony_shore",
            "minecraft:beach",
            "minecraft:snowy_beach",
            "minecraft:river",
            "minecraft:frozen_river",
            "minecraft:swamp",
            "minecraft:mangrove_swamp",
        }:
            return biome

        # First phase: only remote highlands and peaks become End-like.
        if cont_min >= 0.3 and biome in {
            "minecraft:meadow",
            "minecraft:cherry_grove",
            "minecraft:frozen_peaks",
            "minecraft:jagged_peaks",
            "minecraft:snowy_slopes",
            "minecraft:grove",
            "minecraft:windswept_hills",
            "minecraft:windswept_forest",
        }:
            if erosion_max <= -0.78:
                return "minecraft:the_end"
            if weird_min >= 0.4:
                return "minecraft:end_highlands"
            if weird_max <= -0.4:
                return "minecraft:end_barrens"
            return "minecraft:end_midlands"

        return biome

    def add_point(self, biome, temperature, humidity, continentalness, erosion, depth, weirdness):
        biome = self.remap_exotic_biome(biome, temperature, humidity, continentalness, erosion, depth, weirdness)
        corrupted = self.remap.get(biome)
        if corrupted is None:
            raise KeyError(f"Missing corrupted mapping for {biome}")

        payload = {
            "biome": corrupted,
            "parameters": {
                "temperature": to_range(temperature),
                "humidity": to_range(humidity),
                "continentalness": to_range(continentalness),
                "erosion": to_range(erosion),
                "weirdness": to_range(weirdness),
                "depth": to_range(depth),
                "offset": 0.0,
            },
        }
        key = json.dumps(payload, sort_keys=True)
        if key not in self.seen:
            self.seen.add(key)
            self.entries.append(payload)

    def add_surface_biome(self, temperature, humidity, continentalness, erosion, weirdness, biome):
        self.add_point(biome, temperature, humidity, continentalness, erosion, (0.0, 0.0), weirdness)
        self.add_point(biome, temperature, humidity, continentalness, erosion, (1.0, 1.0), weirdness)

    def add_underground_biome(self, temperature, humidity, continentalness, erosion, weirdness, biome):
        self.add_point(biome, temperature, humidity, continentalness, erosion, (0.2, 0.9), weirdness)

    def add_bottom_biome(self, temperature, humidity, continentalness, erosion, weirdness, biome):
        self.add_point(biome, temperature, humidity, continentalness, erosion, (1.1, 1.1), weirdness)

    def add_off_coast_biomes(self):
        self.add_surface_biome(FULL, FULL, MUSHROOM, FULL, FULL, "minecraft:mushroom_fields")
        for i, temperature in enumerate(TEMPS):
            self.add_surface_biome(temperature, FULL, DEEP_OCEAN, FULL, FULL, OCEANS[0][i])
            self.add_surface_biome(temperature, FULL, OCEAN, FULL, FULL, OCEANS[1][i])

    def add_peaks(self, weirdness):
        for i, temperature in enumerate(TEMPS):
            for j, humidity in enumerate(HUMIDITIES):
                middle = pick_middle_biome(i, j, weirdness)
                middle_or_badlands = pick_middle_or_badlands_if_hot(i, j, weirdness)
                middle_or_slope = pick_middle_or_badlands_if_hot_or_slope_if_cold(i, j, weirdness)
                plateau = pick_plateau_biome(i, j, weirdness)
                shattered = pick_shattered_biome(i, j, weirdness)
                windswept = maybe_pick_windswept_savanna_biome(i, j, weirdness, shattered)
                peak = pick_peak_biome(i, j, weirdness)

                self.add_surface_biome(temperature, humidity, span(COAST, FAR_INLAND), EROSIONS[0], weirdness, peak)
                self.add_surface_biome(temperature, humidity, span(COAST, NEAR_INLAND), EROSIONS[1], weirdness, middle_or_slope)
                self.add_surface_biome(temperature, humidity, span(MID_INLAND, FAR_INLAND), EROSIONS[1], weirdness, peak)
                self.add_surface_biome(temperature, humidity, span(COAST, NEAR_INLAND), span(EROSIONS[2], EROSIONS[3]), weirdness, middle)
                self.add_surface_biome(temperature, humidity, span(MID_INLAND, FAR_INLAND), EROSIONS[2], weirdness, plateau)
                self.add_surface_biome(temperature, humidity, MID_INLAND, EROSIONS[3], weirdness, middle_or_badlands)
                self.add_surface_biome(temperature, humidity, FAR_INLAND, EROSIONS[3], weirdness, plateau)
                self.add_surface_biome(temperature, humidity, span(COAST, FAR_INLAND), EROSIONS[4], weirdness, middle)
                self.add_surface_biome(temperature, humidity, span(COAST, NEAR_INLAND), EROSIONS[5], weirdness, windswept)
                self.add_surface_biome(temperature, humidity, span(MID_INLAND, FAR_INLAND), EROSIONS[5], weirdness, shattered)
                self.add_surface_biome(temperature, humidity, span(COAST, FAR_INLAND), EROSIONS[6], weirdness, middle)

    def add_high_slice(self, weirdness):
        for i, temperature in enumerate(TEMPS):
            for j, humidity in enumerate(HUMIDITIES):
                middle = pick_middle_biome(i, j, weirdness)
                middle_or_badlands = pick_middle_or_badlands_if_hot(i, j, weirdness)
                middle_or_slope = pick_middle_or_badlands_if_hot_or_slope_if_cold(i, j, weirdness)
                plateau = pick_plateau_biome(i, j, weirdness)
                shattered = pick_shattered_biome(i, j, weirdness)
                windswept = maybe_pick_windswept_savanna_biome(i, j, weirdness, middle)
                slope = pick_slope_biome(i, j, weirdness)
                peak = pick_peak_biome(i, j, weirdness)

                self.add_surface_biome(temperature, humidity, COAST, span(EROSIONS[0], EROSIONS[1]), weirdness, middle)
                self.add_surface_biome(temperature, humidity, NEAR_INLAND, EROSIONS[0], weirdness, slope)
                self.add_surface_biome(temperature, humidity, span(MID_INLAND, FAR_INLAND), EROSIONS[0], weirdness, peak)
                self.add_surface_biome(temperature, humidity, NEAR_INLAND, EROSIONS[1], weirdness, middle_or_slope)
                self.add_surface_biome(temperature, humidity, span(MID_INLAND, FAR_INLAND), EROSIONS[1], weirdness, slope)
                self.add_surface_biome(temperature, humidity, span(COAST, NEAR_INLAND), span(EROSIONS[2], EROSIONS[3]), weirdness, middle)
                self.add_surface_biome(temperature, humidity, span(MID_INLAND, FAR_INLAND), EROSIONS[2], weirdness, plateau)
                self.add_surface_biome(temperature, humidity, MID_INLAND, EROSIONS[3], weirdness, middle_or_badlands)
                self.add_surface_biome(temperature, humidity, FAR_INLAND, EROSIONS[3], weirdness, plateau)
                self.add_surface_biome(temperature, humidity, span(COAST, FAR_INLAND), EROSIONS[4], weirdness, middle)
                self.add_surface_biome(temperature, humidity, span(COAST, NEAR_INLAND), EROSIONS[5], weirdness, windswept)
                self.add_surface_biome(temperature, humidity, span(MID_INLAND, FAR_INLAND), EROSIONS[5], weirdness, shattered)
                self.add_surface_biome(temperature, humidity, span(COAST, FAR_INLAND), EROSIONS[6], weirdness, middle)

    def add_mid_slice(self, weirdness):
        self.add_surface_biome(FULL, FULL, COAST, span(EROSIONS[0], EROSIONS[2]), weirdness, "minecraft:stony_shore")
        self.add_surface_biome(span(TEMPS[1], TEMPS[2]), FULL, span(NEAR_INLAND, FAR_INLAND), EROSIONS[6], weirdness, "minecraft:swamp")
        self.add_surface_biome(span(TEMPS[3], TEMPS[4]), FULL, span(NEAR_INLAND, FAR_INLAND), EROSIONS[6], weirdness, "minecraft:mangrove_swamp")

        for i, temperature in enumerate(TEMPS):
            for j, humidity in enumerate(HUMIDITIES):
                middle = pick_middle_biome(i, j, weirdness)
                middle_or_badlands = pick_middle_or_badlands_if_hot(i, j, weirdness)
                middle_or_slope = pick_middle_or_badlands_if_hot_or_slope_if_cold(i, j, weirdness)
                shattered = pick_shattered_biome(i, j, weirdness)
                plateau = pick_plateau_biome(i, j, weirdness)
                beach = pick_beach_biome(i, j)
                windswept = maybe_pick_windswept_savanna_biome(i, j, weirdness, middle)
                shattered_coast = pick_shattered_coast_biome(i, j, weirdness)
                slope = pick_slope_biome(i, j, weirdness)

                self.add_surface_biome(temperature, humidity, span(NEAR_INLAND, FAR_INLAND), EROSIONS[0], weirdness, slope)
                self.add_surface_biome(temperature, humidity, span(NEAR_INLAND, MID_INLAND), EROSIONS[1], weirdness, middle_or_slope)
                self.add_surface_biome(temperature, humidity, FAR_INLAND, EROSIONS[1], weirdness, slope if i == 0 else plateau)
                self.add_surface_biome(temperature, humidity, NEAR_INLAND, EROSIONS[2], weirdness, middle)
                self.add_surface_biome(temperature, humidity, MID_INLAND, EROSIONS[2], weirdness, middle_or_badlands)
                self.add_surface_biome(temperature, humidity, FAR_INLAND, EROSIONS[2], weirdness, plateau)
                self.add_surface_biome(temperature, humidity, span(COAST, NEAR_INLAND), EROSIONS[3], weirdness, middle)
                self.add_surface_biome(temperature, humidity, span(MID_INLAND, FAR_INLAND), EROSIONS[3], weirdness, middle_or_badlands)
                if param_max(weirdness) < 0:
                    self.add_surface_biome(temperature, humidity, COAST, EROSIONS[4], weirdness, beach)
                    self.add_surface_biome(temperature, humidity, span(NEAR_INLAND, FAR_INLAND), EROSIONS[4], weirdness, middle)
                else:
                    self.add_surface_biome(temperature, humidity, span(COAST, FAR_INLAND), EROSIONS[4], weirdness, middle)
                self.add_surface_biome(temperature, humidity, COAST, EROSIONS[5], weirdness, shattered_coast)
                self.add_surface_biome(temperature, humidity, NEAR_INLAND, EROSIONS[5], weirdness, windswept)
                self.add_surface_biome(temperature, humidity, span(MID_INLAND, FAR_INLAND), EROSIONS[5], weirdness, shattered)
                self.add_surface_biome(
                    temperature,
                    humidity,
                    COAST,
                    EROSIONS[6],
                    weirdness,
                    beach if param_max(weirdness) < 0 else middle,
                )
                if i == 0:
                    self.add_surface_biome(temperature, humidity, span(NEAR_INLAND, FAR_INLAND), EROSIONS[6], weirdness, middle)

    def add_low_slice(self, weirdness):
        self.add_surface_biome(FULL, FULL, COAST, span(EROSIONS[0], EROSIONS[2]), weirdness, "minecraft:stony_shore")
        self.add_surface_biome(span(TEMPS[1], TEMPS[2]), FULL, span(NEAR_INLAND, FAR_INLAND), EROSIONS[6], weirdness, "minecraft:swamp")
        self.add_surface_biome(span(TEMPS[3], TEMPS[4]), FULL, span(NEAR_INLAND, FAR_INLAND), EROSIONS[6], weirdness, "minecraft:mangrove_swamp")

        for i, temperature in enumerate(TEMPS):
            for j, humidity in enumerate(HUMIDITIES):
                middle = pick_middle_biome(i, j, weirdness)
                middle_or_badlands = pick_middle_or_badlands_if_hot(i, j, weirdness)
                middle_or_slope = pick_middle_or_badlands_if_hot_or_slope_if_cold(i, j, weirdness)
                beach = pick_beach_biome(i, j)
                windswept = maybe_pick_windswept_savanna_biome(i, j, weirdness, middle)
                shattered_coast = pick_shattered_coast_biome(i, j, weirdness)

                self.add_surface_biome(temperature, humidity, NEAR_INLAND, span(EROSIONS[0], EROSIONS[1]), weirdness, middle_or_badlands)
                self.add_surface_biome(temperature, humidity, span(MID_INLAND, FAR_INLAND), span(EROSIONS[0], EROSIONS[1]), weirdness, middle_or_slope)
                self.add_surface_biome(temperature, humidity, NEAR_INLAND, span(EROSIONS[2], EROSIONS[3]), weirdness, middle)
                self.add_surface_biome(temperature, humidity, span(MID_INLAND, FAR_INLAND), span(EROSIONS[2], EROSIONS[3]), weirdness, middle_or_badlands)
                self.add_surface_biome(temperature, humidity, COAST, span(EROSIONS[3], EROSIONS[4]), weirdness, beach)
                self.add_surface_biome(temperature, humidity, span(NEAR_INLAND, FAR_INLAND), EROSIONS[4], weirdness, middle)
                self.add_surface_biome(temperature, humidity, COAST, EROSIONS[5], weirdness, shattered_coast)
                self.add_surface_biome(temperature, humidity, NEAR_INLAND, EROSIONS[5], weirdness, windswept)
                self.add_surface_biome(temperature, humidity, span(MID_INLAND, FAR_INLAND), EROSIONS[5], weirdness, middle)
                self.add_surface_biome(temperature, humidity, COAST, EROSIONS[6], weirdness, beach)
                if i == 0:
                    self.add_surface_biome(temperature, humidity, span(NEAR_INLAND, FAR_INLAND), EROSIONS[6], weirdness, middle)

    def add_valleys(self, weirdness):
        coast_erosion = span(EROSIONS[0], EROSIONS[1])
        inland_erosion = span(EROSIONS[2], EROSIONS[5])
        coast_biome = "minecraft:stony_shore" if param_max(weirdness) < 0 else "minecraft:frozen_river"
        unfrozen_coast_biome = "minecraft:stony_shore" if param_max(weirdness) < 0 else "minecraft:river"

        self.add_surface_biome(FROZEN_RANGE, FULL, COAST, coast_erosion, weirdness, coast_biome)
        self.add_surface_biome(UNFROZEN_RANGE, FULL, COAST, coast_erosion, weirdness, unfrozen_coast_biome)
        self.add_surface_biome(FROZEN_RANGE, FULL, NEAR_INLAND, coast_erosion, weirdness, "minecraft:frozen_river")
        self.add_surface_biome(UNFROZEN_RANGE, FULL, NEAR_INLAND, coast_erosion, weirdness, "minecraft:river")
        self.add_surface_biome(FROZEN_RANGE, FULL, span(COAST, FAR_INLAND), inland_erosion, weirdness, "minecraft:frozen_river")
        self.add_surface_biome(UNFROZEN_RANGE, FULL, span(COAST, FAR_INLAND), inland_erosion, weirdness, "minecraft:river")
        self.add_surface_biome(FROZEN_RANGE, FULL, COAST, EROSIONS[6], weirdness, "minecraft:frozen_river")
        self.add_surface_biome(UNFROZEN_RANGE, FULL, COAST, EROSIONS[6], weirdness, "minecraft:river")
        self.add_surface_biome(span(TEMPS[1], TEMPS[2]), FULL, span(INLAND, FAR_INLAND), EROSIONS[6], weirdness, "minecraft:swamp")
        self.add_surface_biome(span(TEMPS[3], TEMPS[4]), FULL, span(INLAND, FAR_INLAND), EROSIONS[6], weirdness, "minecraft:mangrove_swamp")
        self.add_surface_biome(FROZEN_RANGE, FULL, span(INLAND, FAR_INLAND), EROSIONS[6], weirdness, "minecraft:frozen_river")

        for i, temperature in enumerate(TEMPS):
            for j, humidity in enumerate(HUMIDITIES):
                middle_or_badlands = pick_middle_or_badlands_if_hot(i, j, weirdness)
                self.add_surface_biome(temperature, humidity, span(MID_INLAND, FAR_INLAND), coast_erosion, weirdness, middle_or_badlands)

    def add_underground_biomes(self):
        self.add_underground_biome(FULL, FULL, span(0.8, 1.0), FULL, FULL, "minecraft:dripstone_caves")
        self.add_underground_biome(FULL, span(0.7, 1.0), FULL, FULL, FULL, "minecraft:lush_caves")
        self.add_bottom_biome(FULL, FULL, FULL, span(EROSIONS[0], EROSIONS[1]), FULL, "minecraft:deep_dark")

    def build(self):
        self.add_off_coast_biomes()
        for weirdness in [
            (-1.0, -0.93333334),
            (-0.93333334, -0.7666667),
            (-0.7666667, -0.56666666),
            (-0.56666666, -0.4),
            (-0.4, -0.26666668),
            (-0.26666668, -0.05),
            (-0.05, 0.05),
            (0.05, 0.26666668),
            (0.26666668, 0.4),
            (0.4, 0.56666666),
            (0.56666666, 0.7666667),
            (0.7666667, 0.93333334),
            (0.93333334, 1.0),
        ]:
            if weirdness in [(-1.0, -0.93333334), (-0.4, -0.26666668), (0.26666668, 0.4), (0.93333334, 1.0)]:
                self.add_mid_slice(weirdness)
            elif weirdness in [(-0.93333334, -0.7666667), (-0.56666666, -0.4), (0.4, 0.56666666), (0.7666667, 0.93333334)]:
                self.add_high_slice(weirdness)
            elif weirdness in [(-0.7666667, -0.56666666), (0.56666666, 0.7666667)]:
                self.add_peaks(weirdness)
            elif weirdness in [(-0.26666668, -0.05), (0.05, 0.26666668)]:
                self.add_low_slice(weirdness)
            else:
                self.add_valleys(weirdness)
        self.add_underground_biomes()
        return self.entries


def main():
    parser = argparse.ArgumentParser(description="Generate Exteritio multi-noise biome source from the 1.21.1 Overworld biome builder.")
    parser.add_argument(
        "--inject-exotics",
        action="store_true",
        help="Inject controlled corrupted Nether/End biome swaps into selected climate niches.",
    )
    args = parser.parse_args()

    remap = load_mapping()
    builder = Builder(remap, inject_exotics=args.inject_exotics)
    biomes = builder.build()
    payload = {
        "type": "salvation:exteritio",
        "generator": {
            "type": "minecraft:noise",
            "settings": "salvation:exteritio_noise_settings",
            "biome_source": {
                "type": "minecraft:multi_noise",
                "biomes": biomes,
            },
        },
    }
    OUTPUT_PATH.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {OUTPUT_PATH}")
    print(f"Biome entries: {len(biomes)}")
    print(f"Exotic injection: {'enabled' if args.inject_exotics else 'disabled'}")


if __name__ == "__main__":
    main()
