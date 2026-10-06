package com.momosoftworks.coldsweat.fabric.season;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Optional Ecliptic Seasons bridge.
 *
 * <p>This class deliberately contains all references to Ecliptic's class names
 * so the rest of Cold Sweat can depend only on this loader-safe boundary. If
 * Ecliptic is absent, Cold Sweat keeps its existing behavior unchanged.
 *
 * <p>M9.1 introduced observation-only season metadata. M9.2a added Ecliptic's
 * resolved per-biome seasonal temperature delta. M9.2b additionally exposes a
 * normalized cold-season intensity so Cold Sweat can translate Ecliptic's
 * climate signal into biome-relative winter envelopes without copying
 * Ecliptic's solar-term table or assuming a raw value is Celsius.
 */
public final class SeasonContextService
{
    private static final String ECLIPTIC_MOD_ID = "eclipticseasons";
    private static final long CACHE_TTL_NANOS = 1_000_000_000L;
    private static final long DAY_LENGTH_TICKS = 24_000L;

    private static final Map<Level, CachedContext> CACHE = new WeakHashMap<>();
    private static final Map<Level, String> LAST_LOGGED_CONTEXT = new WeakHashMap<>();
    private static final Map<Level, Map<Biome, CachedClimateSample>> CLIMATE_CACHE =
            new WeakHashMap<>();

    private static volatile Adapter adapter = NoopAdapter.INSTANCE;
    private static volatile boolean initialized;
    private static volatile boolean readFailureLogged;
    private static volatile boolean climateFailureLogged;

    private SeasonContextService()
    {
    }

    public static synchronized void initialize()
    {
        if (initialized)
        {
            return;
        }
        initialized = true;

        if (FabricLoader.getInstance().isModLoaded(ECLIPTIC_MOD_ID))
        {
            try
            {
                adapter = EclipticAdapter.create();
                ColdSweatFabric.LOGGER.info(
                        "Ecliptic Seasons detected; optional seasonal context provider enabled."
                );
            }
            catch (ReflectiveOperationException | LinkageError exception)
            {
                adapter = NoopAdapter.INSTANCE;
                ColdSweatFabric.LOGGER.warn(
                        "Ecliptic Seasons is loaded, but its 26.2 seasonal API could not be bound. "
                                + "Cold Sweat will continue without seasonal integration.",
                        exception
                );
            }
        }
        else
        {
            ColdSweatFabric.LOGGER.info(
                    "Ecliptic Seasons not detected; seasonal context provider inactive."
            );
        }

        ServerLifecycleEvents.SERVER_STARTED.register(server ->
        {
            if (adapter.available())
            {
                // The first actual Ecliptic API invocation is deliberately
                // deferred until every mod has initialized and all levels exist.
                current(server.overworld());
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearCaches());
    }

    /**
     * Returns the current seasonal metadata for a level.
     *
     * <p>Reads are cached for one second. The cache is intentionally short:
     * solar terms change very slowly, while commands or test tooling can still
     * force a season change without leaving stale data around for long.
     */
    public static SeasonContext current(Level level)
    {
        Objects.requireNonNull(level, "level");

        if (!initialized)
        {
            initialize();
        }

        long now = System.nanoTime();
        synchronized (CACHE)
        {
            CachedContext cached = CACHE.get(level);
            if (cached != null && now < cached.expiresAtNanos())
            {
                return cached.context();
            }
        }

        SeasonContext context = readUncached(level);

        synchronized (CACHE)
        {
            CACHE.put(
                    level,
                    new CachedContext(
                            context,
                            now + CACHE_TTL_NANOS
                    )
            );
        }

        logIfChanged(level, context);
        return context;
    }

    /**
     * Returns Ecliptic's resolved climate signal for one biome.
     *
     * <p>{@link BiomeClimateSample#offset()} remains in Minecraft/Cold Sweat
     * WORLD-temperature units. {@link BiomeClimateSample#coldIntensity()} is a
     * dimensionless seasonal-strength signal:
     *
     * <ul>
     *     <li>0 = no cold-season depression</li>
     *     <li>1 = the biome reaches Ecliptic's canonical full cold amplitude</li>
     *     <li>values up to 1.25 are allowed for datapacks that deliberately
     *         specify stronger-than-default seasonal cooling</li>
     * </ul>
     *
     * <p>The intensity is derived from Ecliptic's actual resolved biome climate
     * data and Ecliptic's own enum values at runtime. No copied -0.45 constant
     * or hard-coded solar-term table lives in Cold Sweat.
     */
    public static BiomeClimateSample getBiomeClimateSample(
            Level level,
            Biome biome
    )
    {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(biome, "biome");

        SeasonContext context = current(level);
        if (!context.hasSeason() || !adapter.available())
        {
            return BiomeClimateSample.NONE;
        }

        long now = System.nanoTime();

        synchronized (CLIMATE_CACHE)
        {
            Map<Biome, CachedClimateSample> levelCache =
                    CLIMATE_CACHE.computeIfAbsent(
                            level,
                            ignored -> new IdentityHashMap<>()
                    );

            CachedClimateSample cached = levelCache.get(biome);
            if (cached != null && now < cached.expiresAtNanos())
            {
                return cached.sample();
            }
        }

        BiomeClimateSample sample =
                readBiomeClimateSampleUncached(level, biome);

        synchronized (CLIMATE_CACHE)
        {
            CLIMATE_CACHE
                    .computeIfAbsent(
                            level,
                            ignored -> new IdentityHashMap<>()
                    )
                    .put(
                            biome,
                            new CachedClimateSample(
                                    sample,
                                    now + CACHE_TTL_NANOS
                            )
                    );
        }

        return sample;
    }

    /**
     * Compatibility helper retained from M9.2a.
     */
    public static double getBiomeTemperatureOffset(
            Level level,
            Biome biome
    )
    {
        return getBiomeClimateSample(level, biome).offset();
    }

    public static boolean isEclipticAvailable()
    {
        if (!initialized)
        {
            initialize();
        }
        return adapter.available();
    }

    private static SeasonContext readUncached(Level level)
    {
        try
        {
            return adapter.read(level);
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError exception)
        {
            if (!readFailureLogged)
            {
                readFailureLogged = true;
                ColdSweatFabric.LOGGER.warn(
                        "Failed to read Ecliptic seasonal context. "
                                + "This read will fall back to no seasonal context.",
                        exception
                );
            }
            return SeasonContext.NONE;
        }
    }

    private static BiomeClimateSample readBiomeClimateSampleUncached(
            Level level,
            Biome biome
    )
    {
        try
        {
            return adapter.readBiomeClimateSample(level, biome);
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError exception)
        {
            if (!climateFailureLogged)
            {
                climateFailureLogged = true;
                ColdSweatFabric.LOGGER.warn(
                        "Failed to read Ecliptic biome climate data. "
                                + "Cold Sweat will ignore the seasonal biome signal for this read.",
                        exception
                );
            }
            return BiomeClimateSample.NONE;
        }
    }

    private static void logIfChanged(Level level, SeasonContext context)
    {
        if (context.source() == SeasonContext.Source.NONE)
        {
            return;
        }

        String signature = context.active()
                + "|" + context.solarTermId()
                + "|" + context.seasonId();

        synchronized (LAST_LOGGED_CONTEXT)
        {
            if (signature.equals(LAST_LOGGED_CONTEXT.get(level)))
            {
                return;
            }
            LAST_LOGGED_CONTEXT.put(level, signature);
        }

        ColdSweatFabric.LOGGER.info(
                "Season context changed: active={}, term={} ({}), season={} ({}), "
                        + "rawClimateScalar={} [not Celsius]",
                context.active(),
                context.solarTermId(),
                context.solarTermIndex(),
                context.seasonId(),
                context.seasonIndex(),
                context.rawClimateChangeScalar()
        );
    }

    private static void clearCaches()
    {
        synchronized (CACHE)
        {
            CACHE.clear();
        }
        synchronized (LAST_LOGGED_CONTEXT)
        {
            LAST_LOGGED_CONTEXT.clear();
        }
        synchronized (CLIMATE_CACHE)
        {
            CLIMATE_CACHE.clear();
        }
        readFailureLogged = false;
        climateFailureLogged = false;
    }

    private interface Adapter
    {
        SeasonContext read(Level level) throws ReflectiveOperationException;

        BiomeClimateSample readBiomeClimateSample(
                Level level,
                Biome biome
        ) throws ReflectiveOperationException;

        boolean available();
    }

    private enum NoopAdapter implements Adapter
    {
        INSTANCE;

        @Override
        public SeasonContext read(Level level)
        {
            return SeasonContext.NONE;
        }

        @Override
        public BiomeClimateSample readBiomeClimateSample(
                Level level,
                Biome biome
        )
        {
            return BiomeClimateSample.NONE;
        }

        @Override
        public boolean available()
        {
            return false;
        }
    }

    /**
     * Reflection is intentional here.
     *
     * <p>Ecliptic is optional and its live 26.2 branch can move ahead of the
     * latest published artifact. Public calendar state is read from
     * EclipticSeasonsApi. Ecliptic does not currently expose its resolved
     * per-biome seasonal temperature delta through that interface, so M9.2
     * isolates the one internal semantic read (BiomeClimateManager) here and
     * fails soft if that internal shape changes.
     */
    private static final class EclipticAdapter implements Adapter
    {
        private static final String API_CLASS =
                "com.teamtea.eclipticseasons.api.EclipticSeasonsApi";
        private static final String SOLAR_TERM_CLASS =
                "com.teamtea.eclipticseasons.api.constant.solar.SolarTerm";
        private static final String BIOME_CLIMATE_MANAGER_CLASS =
                "com.teamtea.eclipticseasons.common.core.biome.BiomeClimateManager";
        private static final String BIOME_CLIMATE_SETTINGS_CLASS =
                "com.teamtea.eclipticseasons.api.data.climate.BiomeClimateSettings";

        private final Method getInstance;
        private final Method getSolarTerm;
        private final Method isSeasonEnabled;
        private final Method getTemperatureChange;
        private final Method getSeason;

        private final Method getDayInTerm;
        private final Method getLastingDaysOfEachTerm;
        private final Method getNextSolarTerm;
        private final Method getBiomeClimateSettings;
        private final Method getBiomeTemperatureChange;

        private final Object[] solarTerms;

        private volatile Object api;

        private EclipticAdapter(
                Method getInstance,
                Method getSolarTerm,
                Method isSeasonEnabled,
                Method getTemperatureChange,
                Method getSeason,
                Method getDayInTerm,
                Method getLastingDaysOfEachTerm,
                Method getNextSolarTerm,
                Method getBiomeClimateSettings,
                Method getBiomeTemperatureChange,
                Object[] solarTerms
        )
        {
            this.getInstance = getInstance;
            this.getSolarTerm = getSolarTerm;
            this.isSeasonEnabled = isSeasonEnabled;
            this.getTemperatureChange = getTemperatureChange;
            this.getSeason = getSeason;
            this.getDayInTerm = getDayInTerm;
            this.getLastingDaysOfEachTerm = getLastingDaysOfEachTerm;
            this.getNextSolarTerm = getNextSolarTerm;
            this.getBiomeClimateSettings = getBiomeClimateSettings;
            this.getBiomeTemperatureChange = getBiomeTemperatureChange;
            this.solarTerms = solarTerms;
        }

        static EclipticAdapter create() throws ReflectiveOperationException
        {
            ClassLoader loader = SeasonContextService.class.getClassLoader();

            // Do not initialize Ecliptic classes here. Fabric only guarantees
            // that the mod is present, not that its entrypoint ran before ours.
            Class<?> apiClass = Class.forName(
                    API_CLASS,
                    false,
                    loader
            );
            Class<?> solarTermClass = Class.forName(
                    SOLAR_TERM_CLASS,
                    false,
                    loader
            );
            Class<?> biomeClimateManagerClass = Class.forName(
                    BIOME_CLIMATE_MANAGER_CLASS,
                    false,
                    loader
            );
            Class<?> biomeClimateSettingsClass = Class.forName(
                    BIOME_CLIMATE_SETTINGS_CLASS,
                    false,
                    loader
            );

            Object[] solarTerms = solarTermClass.getEnumConstants();
            if (solarTerms == null || solarTerms.length == 0)
            {
                throw new ReflectiveOperationException(
                        "Ecliptic SolarTerm enum did not expose constants"
                );
            }

            return new EclipticAdapter(
                    apiClass.getMethod("getInstance"),
                    apiClass.getMethod("getSolarTerm", Level.class),
                    apiClass.getMethod("isSeasonEnabled", Level.class),
                    solarTermClass.getMethod("getTemperatureChange"),
                    solarTermClass.getMethod("getSeason"),
                    apiClass.getMethod("getDayInTerm", Level.class),
                    apiClass.getMethod("getLastingDaysOfEachTerm", Level.class),
                    solarTermClass.getMethod("getNextSolarTerm"),
                    biomeClimateManagerClass.getMethod(
                            "getBiomeClimateSettings",
                            Biome.class,
                            boolean.class
                    ),
                    biomeClimateSettingsClass.getMethod(
                            "getTemperatureChange",
                            solarTermClass
                    ),
                    solarTerms
            );
        }

        @Override
        public SeasonContext read(Level level) throws ReflectiveOperationException
        {
            Object api = getApi();
            Object solarTerm = getSolarTerm.invoke(api, level);
            if (!(solarTerm instanceof Enum<?> solarTermEnum))
            {
                return SeasonContext.NONE;
            }

            Object season = getSeason.invoke(solarTerm);
            if (!(season instanceof Enum<?> seasonEnum))
            {
                return SeasonContext.NONE;
            }

            boolean enabled = (boolean) isSeasonEnabled.invoke(api, level);
            float rawClimateScalar =
                    ((Number) getTemperatureChange.invoke(solarTerm)).floatValue();

            String solarTermId = solarTermEnum.name();
            String seasonId = seasonEnum.name();

            boolean active = enabled
                    && !"NONE".equals(solarTermId)
                    && !"NONE".equals(seasonId);

            return new SeasonContext(
                    SeasonContext.Source.ECLIPTIC_SEASONS,
                    active,
                    solarTermEnum.ordinal(),
                    solarTermId,
                    seasonEnum.ordinal(),
                    seasonId,
                    rawClimateScalar
            );
        }

        @Override
        public BiomeClimateSample readBiomeClimateSample(
                Level level,
                Biome biome
        ) throws ReflectiveOperationException
        {
            Object api = getApi();
            Object solarTerm = getSolarTerm.invoke(api, level);

            if (!(solarTerm instanceof Enum<?> solarTermEnum)
                    || "NONE".equals(solarTermEnum.name()))
            {
                return BiomeClimateSample.NONE;
            }

            boolean enabled = (boolean) isSeasonEnabled.invoke(api, level);
            if (!enabled)
            {
                return BiomeClimateSample.NONE;
            }

            Object nextSolarTerm = getNextSolarTerm.invoke(solarTerm);
            Object settings = getBiomeClimateSettings.invoke(
                    null,
                    biome,
                    level instanceof ServerLevel
            );

            double currentOffset =
                    ((Number) getBiomeTemperatureChange.invoke(
                            settings,
                            solarTerm
                    )).doubleValue();

            double nextOffset =
                    ((Number) getBiomeTemperatureChange.invoke(
                            settings,
                            nextSolarTerm
                    )).doubleValue();

            int lastingDays =
                    ((Number) getLastingDaysOfEachTerm.invoke(
                            api,
                            level
                    )).intValue();

            double interpolatedOffset = currentOffset;

            if (lastingDays > 0)
            {
                int dayInTerm =
                        ((Number) getDayInTerm.invoke(
                                api,
                                level
                        )).intValue();

                double dayFraction = level.dimensionType().hasFixedTime()
                        ? 0.0
                        : Math.floorMod(
                                level.getOverworldClockTime(),
                                DAY_LENGTH_TICKS
                        ) / (double) DAY_LENGTH_TICKS;

                double progress =
                        clamp(
                                (dayInTerm + dayFraction) / lastingDays,
                                0.0,
                                1.0
                        );

                interpolatedOffset =
                        currentOffset
                                + (nextOffset - currentOffset) * progress;
            }

            /*
             * Resolve the cold amplitude dynamically from Ecliptic itself.
             * biomeMinimum reflects datapack/custom BiomeClimateSettings;
             * rawMinimum reflects Ecliptic's canonical solar-term amplitude.
             * Their ratio lets a datapack deliberately weaken/strengthen a
             * biome's seasonal response without Cold Sweat copying -0.45.
             */
            double biomeMinimum = 0.0;
            double rawMinimum = 0.0;

            for (Object term : solarTerms)
            {
                if (term instanceof Enum<?> termEnum
                        && "NONE".equals(termEnum.name()))
                {
                    continue;
                }

                double biomeDelta =
                        ((Number) getBiomeTemperatureChange.invoke(
                                settings,
                                term
                        )).doubleValue();

                double rawDelta =
                        ((Number) getTemperatureChange.invoke(
                                term
                        )).doubleValue();

                biomeMinimum = Math.min(biomeMinimum, biomeDelta);
                rawMinimum = Math.min(rawMinimum, rawDelta);
            }

            double coldIntensity = 0.0;

            if (interpolatedOffset < 0.0
                    && biomeMinimum < 0.0
                    && rawMinimum < 0.0)
            {
                double progressWithinBiomeColdRange =
                        clamp(
                                interpolatedOffset / biomeMinimum,
                                0.0,
                                1.0
                        );

                double biomeAmplitudeRatio =
                        Math.abs(biomeMinimum / rawMinimum);

                coldIntensity =
                        clamp(
                                progressWithinBiomeColdRange
                                        * biomeAmplitudeRatio,
                                0.0,
                                1.25
                        );
            }

            return new BiomeClimateSample(
                    interpolatedOffset,
                    coldIntensity
            );
        }

        private Object getApi() throws ReflectiveOperationException
        {
            Object current = api;
            if (current != null)
            {
                return current;
            }

            synchronized (this)
            {
                if (api == null)
                {
                    api = getInstance.invoke(null);
                }
                return api;
            }
        }

        @Override
        public boolean available()
        {
            return true;
        }

        private static double clamp(
                double value,
                double min,
                double max
        )
        {
            return Math.max(min, Math.min(max, value));
        }
    }

    public record BiomeClimateSample(
            double offset,
            double coldIntensity
    )
    {
        public static final BiomeClimateSample NONE =
                new BiomeClimateSample(0.0, 0.0);
    }

    private record CachedContext(
            SeasonContext context,
            long expiresAtNanos
    )
    {
    }

    private record CachedClimateSample(
            BiomeClimateSample sample,
            long expiresAtNanos
    )
    {
    }
}
