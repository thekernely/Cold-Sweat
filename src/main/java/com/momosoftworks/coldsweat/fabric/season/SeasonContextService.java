package com.momosoftworks.coldsweat.fabric.season;

import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Optional Ecliptic Seasons bridge.
 *
 * <p>This class deliberately contains all references to Ecliptic's class names
 * so the rest of Cold Sweat can depend only on {@link SeasonContext}. If
 * Ecliptic is absent, Cold Sweat keeps its existing behavior unchanged.
 *
 * <p>M9.1 is observation-only. This service does not modify world, player,
 * biome, or body temperatures.
 */
public final class SeasonContextService
{
    private static final String ECLIPTIC_MOD_ID = "eclipticseasons";
    private static final long CACHE_TTL_NANOS = 1_000_000_000L;

    private static final Map<Level, CachedContext> CACHE = new WeakHashMap<>();
    private static final Map<Level, String> LAST_LOGGED_CONTEXT = new WeakHashMap<>();

    private static volatile Adapter adapter = NoopAdapter.INSTANCE;
    private static volatile boolean initialized;
    private static volatile boolean readFailureLogged;

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
        readFailureLogged = false;
    }

    private interface Adapter
    {
        SeasonContext read(Level level) throws ReflectiveOperationException;

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
        public boolean available()
        {
            return false;
        }
    }

    /**
     * Reflection is intentional here.
     *
     * <p>Ecliptic is optional and its live 26.2 branch can move ahead of the
     * latest published artifact. Binding the supported public API once at
     * startup keeps Cold Sweat free of a hard runtime dependency while avoiding
     * duplicated solar-term tables or localized-name parsing.
     */
    private static final class EclipticAdapter implements Adapter
    {
        private static final String API_CLASS =
                "com.teamtea.eclipticseasons.api.EclipticSeasonsApi";
        private static final String SOLAR_TERM_CLASS =
                "com.teamtea.eclipticseasons.api.constant.solar.SolarTerm";

        private final Method getInstance;
        private final Method getSolarTerm;
        private final Method isSeasonEnabled;
        private final Method getTemperatureChange;
        private final Method getSeason;

        private volatile Object api;

        private EclipticAdapter(
                Method getInstance,
                Method getSolarTerm,
                Method isSeasonEnabled,
                Method getTemperatureChange,
                Method getSeason
        )
        {
            this.getInstance = getInstance;
            this.getSolarTerm = getSolarTerm;
            this.isSeasonEnabled = isSeasonEnabled;
            this.getTemperatureChange = getTemperatureChange;
            this.getSeason = getSeason;
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

            return new EclipticAdapter(
                    apiClass.getMethod("getInstance"),
                    apiClass.getMethod("getSolarTerm", Level.class),
                    apiClass.getMethod("isSeasonEnabled", Level.class),
                    solarTermClass.getMethod("getTemperatureChange"),
                    solarTermClass.getMethod("getSeason")
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
    }

    private record CachedContext(
            SeasonContext context,
            long expiresAtNanos
    )
    {
    }
}
