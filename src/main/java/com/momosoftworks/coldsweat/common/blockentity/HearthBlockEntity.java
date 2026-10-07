package com.momosoftworks.coldsweat.common.blockentity;

import com.momosoftworks.coldsweat.api.registry.ThermalFuelRegistry;
import com.momosoftworks.coldsweat.common.block.HearthBottomBlock;
import com.momosoftworks.coldsweat.common.block.SmokestackBlock;
import com.momosoftworks.coldsweat.core.init.ModBlockEntities;
import com.momosoftworks.coldsweat.core.init.ModBlocks;
import com.momosoftworks.coldsweat.fabric.temperature.RoomThermalManager;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HearthBlockEntity extends BlockEntity implements Container
{
    public static final int MAX_FUEL = 1000;
    public static final int ROOM_TEMP_UNAVAILABLE = -10000;

    protected static final int FUEL_INTERVAL = 40;
    protected static final int EFFECT_INTERVAL = 20;
    protected static final int THERMAL_RANGE = 16;
    protected static final int MAX_THERMAL_RANGE = 96;
    protected static final int WARM_UP_TIME = 1200;
    protected static final int SPREAD_REBUILD_INTERVAL = 40;
    protected static final int MAX_SPREAD_VOLUME = 4096;

    private static final int DEFAULT_CLIMATE_TARGET_TENTHS_C = 190;
    private static final int MIN_CLIMATE_TARGET_TENTHS_C = -200;
    private static final int MAX_CLIMATE_TARGET_TENTHS_C = 500;
    private static final int CLIMATE_HYSTERESIS_TENTHS_C = 20;

    private final NonNullList<ItemStack> items;

    private int hotFuel;
    private int coldFuel;
    private int ticksExisted;
    private int insulationLevel;

    private boolean usingHotFuel;
    private boolean usingColdFuel;

    private boolean climateControlEnabled;
    private int climateTargetTenthsC = DEFAULT_CLIMATE_TARGET_TENTHS_C;
    private int thermostatMode;

    private final ThermalFluidStorage hotFluidStorage =
            new ThermalFluidStorage(this, ThermalFluidStorage.FuelKind.HOT);
    private final ThermalFluidStorage coldFluidStorage =
            new ThermalFluidStorage(this, ThermalFluidStorage.FuelKind.COLD);
    private final Storage<FluidVariant> fluidStorage =
            new CombinedStorage<>(List.of(hotFluidStorage, coldFluidStorage));

    private final Set<BlockPos> spreadPositions = new HashSet<>();

    public HearthBlockEntity(BlockPos pos, BlockState state)
    {
        this(ModBlockEntities.HEARTH, pos, state, 1);
    }

    protected HearthBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state)
    {
        this(type, pos, state, 1);
    }

    protected HearthBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int containerSize)
    {
        super(type, pos, state);
        items = NonNullList.withSize(Math.max(1, containerSize), ItemStack.EMPTY);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, HearthBlockEntity blockEntity)
    {
        blockEntity.tickCommon();

        if (level.isClientSide())
        {
            return;
        }

        boolean heatingDemand;
        boolean coolingDemand;

        if (blockEntity.climateControlEnabled)
        {
            blockEntity.updateClimateControlDemand(level);
            heatingDemand = blockEntity.thermostatMode > 0;
            coolingDemand = blockEntity.thermostatMode < 0;
        }
        else
        {
            blockEntity.thermostatMode = 0;
            heatingDemand = blockEntity.hasThermalOutlet(level) && blockEntity.hasHeatingSignal(level);
            coolingDemand = blockEntity.hasThermalOutlet(level) && blockEntity.hasCoolingSignal(level);
        }

        if (blockEntity.getTicksExisted() % FUEL_INTERVAL == 0)
        {
            blockEntity.tryLoadHearthFuel(heatingDemand, coolingDemand);
        }

        blockEntity.usingHotFuel = heatingDemand && blockEntity.getHotFuel() > 0;
        blockEntity.usingColdFuel = coolingDemand && blockEntity.getColdFuel() > 0;

        if (blockEntity.getTicksExisted() % EFFECT_INTERVAL == 0)
        {
            blockEntity.spawnThermalAirParticles(level, blockEntity.usingHotFuel, blockEntity.usingColdFuel);
        }

        blockEntity.syncHearthBlockState(level, state);

        if (blockEntity.getTicksExisted() % FUEL_INTERVAL == 0)
        {
            if (blockEntity.usingHotFuel)
            {
                blockEntity.setHotFuel(blockEntity.getHotFuel() - 1);
            }
            if (blockEntity.usingColdFuel)
            {
                blockEntity.setColdFuel(blockEntity.getColdFuel() - 1);
            }
        }
    }

    protected void tickCommon()
    {
        ticksExisted++;

        if (level != null && !level.isClientSide())
        {
            if (hasFuel())
            {
                if (spreadPositions.isEmpty() || ticksExisted % SPREAD_REBUILD_INTERVAL == 0)
                {
                    rebuildSpreadPositions(level);
                }
            }
            else if (!spreadPositions.isEmpty())
            {
                spreadPositions.clear();
            }
        }

        if (hasFuel() && insulationLevel < WARM_UP_TIME)
        {
            insulationLevel++;
        }
    }

    private void updateClimateControlDemand(Level level)
    {
        if (!(level instanceof ServerLevel serverLevel))
        {
            thermostatMode = 0;
            return;
        }

        double roomC = RoomThermalManager.peekRoomTemperatureC(serverLevel, getRoomProbePos(level));
        if (!Double.isFinite(roomC))
        {
            thermostatMode = 0;
            return;
        }

        double targetC = climateTargetTenthsC / 10.0;
        double hysteresisC = CLIMATE_HYSTERESIS_TENTHS_C / 10.0;

        if (thermostatMode > 0)
        {
            if (roomC >= targetC) thermostatMode = 0;
            return;
        }

        if (thermostatMode < 0)
        {
            if (roomC <= targetC) thermostatMode = 0;
            return;
        }

        if (roomC <= targetC - hysteresisC)
        {
            thermostatMode = 1;
        }
        else if (roomC >= targetC + hysteresisC)
        {
            thermostatMode = -1;
        }
    }

    protected boolean hasThermalOutlet(Level level)
    {
        return true;
    }

    protected List<Direction> getHeatingSides()
    {
        return List.of(Direction.EAST, Direction.SOUTH);
    }

    protected List<Direction> getCoolingSides()
    {
        return List.of(Direction.WEST, Direction.DOWN);
    }

    protected boolean hasHeatingSignal(Level level)
    {
        return hasSignalOnSides(level, getHeatingSides());
    }

    protected boolean hasCoolingSignal(Level level)
    {
        return hasSignalOnSides(level, getCoolingSides());
    }

    private boolean hasSignalOnSides(Level level, List<Direction> relativeSides)
    {
        Direction facing = getBlockState().hasProperty(HearthBottomBlock.FACING)
                ? getBlockState().getValue(HearthBottomBlock.FACING)
                : Direction.NORTH;

        for (Direction side : relativeSides)
        {
            Direction rotated = rotateFromNorth(side, facing);
            if (level.hasSignal(getBlockPos().relative(rotated), rotated))
            {
                return true;
            }
        }
        return false;
    }

    private static Direction rotateFromNorth(Direction side, Direction facing)
    {
        if (side.getAxis() == Direction.Axis.Y) return side;

        return switch (facing)
        {
            case NORTH -> side;
            case SOUTH -> side.getOpposite();
            case EAST -> switch (side)
            {
                case NORTH -> Direction.EAST;
                case EAST -> Direction.SOUTH;
                case SOUTH -> Direction.WEST;
                case WEST -> Direction.NORTH;
                default -> side;
            };
            case WEST -> switch (side)
            {
                case NORTH -> Direction.WEST;
                case WEST -> Direction.SOUTH;
                case SOUTH -> Direction.EAST;
                case EAST -> Direction.NORTH;
                default -> side;
            };
            default -> side;
        };
    }

    private void syncHearthBlockState(Level level, BlockState state)
    {
        if (!state.hasProperty(HearthBottomBlock.HEATING))
        {
            return;
        }

        BlockState next = state
                .setValue(HearthBottomBlock.HEATING, usingHotFuel)
                .setValue(HearthBottomBlock.COOLING, usingColdFuel)
                .setValue(HearthBottomBlock.LIT, usingHotFuel)
                .setValue(HearthBottomBlock.FROSTED, usingColdFuel)
                .setValue(HearthBottomBlock.SMART, climateControlEnabled);

        if (!next.equals(state))
        {
            level.setBlock(getBlockPos(), next, 3);
            level.getLightEngine().checkBlock(getBlockPos());
        }
    }

    protected void spawnThermalAirParticles(Level level, boolean hot, boolean cold)
    {
        if (!(level instanceof ServerLevel serverLevel)
                || (!hot && !cold)
                || spreadPositions.isEmpty())
        {
            return;
        }

        ArrayList<BlockPos> candidates = new ArrayList<>(spreadPositions);
        int count = Math.min(4, candidates.size());

        for (int i = 0; i < count; i++)
        {
            int index = serverLevel.getRandom().nextInt(candidates.size());
            BlockPos particlePos = candidates.remove(index);

            serverLevel.sendParticles(
                    hot ? ParticleTypes.SMOKE : ParticleTypes.CLOUD,
                    particlePos.getX() + 0.25 + serverLevel.getRandom().nextDouble() * 0.5,
                    particlePos.getY() + 0.25 + serverLevel.getRandom().nextDouble() * 0.5,
                    particlePos.getZ() + 0.25 + serverLevel.getRandom().nextDouble() * 0.5,
                    1,
                    0.02,
                    hot ? 0.02 : 0.005,
                    0.02,
                    0.0
            );
        }
    }

    protected void rebuildSpreadPositions(Level level)
    {
        spreadPositions.clear();

        BlockPos source = getSpreadOrigin(level);
        if (!level.isLoaded(source)) return;

        ArrayDeque<SpreadNode> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        if (canOccupySpreadPosition(level, source)
                && (isTransferMedium(level, source) || !level.canSeeSky(source)))
        {
            open.add(new SpreadNode(source, source));
            visited.add(source);
        }

        while (!open.isEmpty() && spreadPositions.size() < MAX_SPREAD_VOLUME)
        {
            SpreadNode node = open.removeFirst();
            BlockPos current = node.pos();
            boolean currentTransfer = isTransferMedium(level, current);

            if (!withinGlobalRange(current)
                    || (!currentTransfer && level.canSeeSky(current)))
            {
                continue;
            }

            spreadPositions.add(current.immutable());

            for (Direction direction : Direction.values())
            {
                BlockPos next = current.relative(direction);

                if (!visited.add(next)
                        || !level.isLoaded(next)
                        || !withinGlobalRange(next)
                        || !canTraverse(level, current, next, direction))
                {
                    continue;
                }

                boolean nextTransfer = isTransferMedium(level, next);
                BlockPos localOrigin = nextTransfer ? next : node.origin();

                if (!nextTransfer && !withinLocalRange(localOrigin, next))
                {
                    continue;
                }

                open.addLast(new SpreadNode(next, localOrigin));
            }
        }
    }

    protected BlockPos getSpreadOrigin(Level level)
    {
        BlockPos source = getBlockPos().above();
        if (level.getBlockState(source).is(ModBlocks.HEARTH_TOP))
        {
            source = source.above();
        }
        return source;
    }

    protected BlockPos getRoomProbePos(Level level)
    {
        BlockPos above = getBlockPos().above();
        if (level.getBlockState(above).is(ModBlocks.HEARTH_TOP)
                || level.getBlockState(above).getBlock() instanceof SmokestackBlock)
        {
            return above.above();
        }
        return above;
    }

    public int getRoomTemperatureTenthsForMenu()
    {
        if (!(level instanceof ServerLevel serverLevel))
        {
            return ROOM_TEMP_UNAVAILABLE;
        }

        double roomC = RoomThermalManager.peekRoomTemperatureC(serverLevel, getRoomProbePos(level));
        return Double.isFinite(roomC)
                ? (int) Math.round(roomC * 10.0)
                : ROOM_TEMP_UNAVAILABLE;
    }

    private boolean withinGlobalRange(BlockPos target)
    {
        BlockPos machine = getBlockPos();
        return Math.abs(target.getX() - machine.getX()) <= MAX_THERMAL_RANGE
                && Math.abs(target.getY() - machine.getY()) <= MAX_THERMAL_RANGE
                && Math.abs(target.getZ() - machine.getZ()) <= MAX_THERMAL_RANGE;
    }

    private static boolean withinLocalRange(BlockPos origin, BlockPos target)
    {
        return Math.abs(target.getX() - origin.getX()) <= THERMAL_RANGE
                && Math.abs(target.getY() - origin.getY()) <= THERMAL_RANGE
                && Math.abs(target.getZ() - origin.getZ()) <= THERMAL_RANGE;
    }

    private static boolean canTraverse(Level level, BlockPos current, BlockPos next, Direction direction)
    {
        BlockState currentState = level.getBlockState(current);
        BlockState nextState = level.getBlockState(next);

        if (currentState.getBlock() instanceof SmokestackBlock
                && !SmokestackBlock.allowsDirection(currentState, direction))
        {
            return false;
        }

        if (nextState.getBlock() instanceof SmokestackBlock
                && !SmokestackBlock.allowsDirection(nextState, direction))
        {
            return false;
        }

        return canOccupySpreadPosition(level, next);
    }

    private static boolean canOccupySpreadPosition(Level level, BlockPos pos)
    {
        return isTransferMedium(level, pos) || canSpreadThrough(level, pos);
    }

    private static boolean isTransferMedium(Level level, BlockPos pos)
    {
        return level.getBlockState(pos).getBlock() instanceof SmokestackBlock;
    }

    private static boolean canSpreadThrough(Level level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return state.isAir()
                || !state.getFluidState().isEmpty()
                || state.getCollisionShape(level, pos).isEmpty();
    }

    private record SpreadNode(BlockPos pos, BlockPos origin) {}

    private void tryLoadHearthFuel(boolean allowHot, boolean allowCold)
    {
        ItemStack fuelStack = getItem(0);
        int fuelValue = ThermalFuelRegistry.getHearthFuel(fuelStack);

        if (fuelValue == 0) return;

        boolean hot = fuelValue > 0;
        if (hot && (!allowHot || getHotFuel() > 0)) return;
        if (!hot && (!allowCold || getColdFuel() > 0)) return;

        int magnitude = Math.min(getMaxFuel(), Math.abs(fuelValue));
        if (hot) setHotFuel(magnitude);
        else setColdFuel(magnitude);

        if (fuelStack.is(Items.LAVA_BUCKET) || fuelStack.is(Items.POWDER_SNOW_BUCKET))
        {
            setItem(0, new ItemStack(Items.BUCKET));
        }
        else
        {
            fuelStack.shrink(1);
            if (fuelStack.isEmpty()) setItem(0, ItemStack.EMPTY);
            else setChanged();
        }
    }

    public int getTicksExisted() { return ticksExisted; }
    public int getMaxFuel() { return MAX_FUEL; }
    public int getHotFuel() { return hotFuel; }
    public int getColdFuel() { return coldFuel; }
    public ThermalFluidStorage getHotFluidStorage() { return hotFluidStorage; }
    public ThermalFluidStorage getColdFluidStorage() { return coldFluidStorage; }
    public Storage<FluidVariant> getFluidStorage() { return fluidStorage; }

    public void setHotFuel(int amount)
    {
        hotFuel = clampFuel(amount);
        hotFluidStorage.syncFromFuel(hotFuel);
        setChanged();
    }

    public void setColdFuel(int amount)
    {
        coldFuel = clampFuel(amount);
        coldFluidStorage.syncFromFuel(coldFuel);
        setChanged();
    }

    public void addHotFuel(int amount) { setHotFuel(hotFuel + amount); }
    public void addColdFuel(int amount) { setColdFuel(coldFuel + amount); }
    public boolean hasFuel() { return hotFuel > 0 || coldFuel > 0; }
    public boolean isUsingHotFuel() { return usingHotFuel; }
    public boolean isUsingColdFuel() { return usingColdFuel; }

    public boolean isClimateControlEnabled() { return climateControlEnabled; }

    public void setClimateControlEnabled(boolean enabled)
    {
        climateControlEnabled = enabled;
        if (!enabled) thermostatMode = 0;
        setChanged();
    }

    public int getClimateTargetTenthsC() { return climateTargetTenthsC; }

    public void adjustClimateTargetC(int degrees)
    {
        climateTargetTenthsC = Math.max(
                MIN_CLIMATE_TARGET_TENTHS_C,
                Math.min(MAX_CLIMATE_TARGET_TENTHS_C, climateTargetTenthsC + degrees * 10)
        );
        setChanged();
    }

    public int getThermostatMode()
    {
        if (usingHotFuel) return 1;
        if (usingColdFuel) return -1;
        return 0;
    }

    private int clampFuel(int amount)
    {
        return Math.max(0, Math.min(getMaxFuel(), amount));
    }

    @Override public int getContainerSize() { return items.size(); }

    @Override
    public boolean isEmpty()
    {
        for (ItemStack stack : items)
        {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount)
    {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot)
    {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack)
    {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player player)
    {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(
                        worldPosition.getX() + 0.5,
                        worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5
                ) <= 64.0;
    }

    @Override
    public void clearContent()
    {
        items.clear();
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("HotFuel", hotFuel);
        output.putInt("ColdFuel", coldFuel);
        output.putInt("TicksExisted", ticksExisted);
        output.putInt("InsulationLevel", insulationLevel);
        output.putBoolean("ClimateControlEnabled", climateControlEnabled);
        output.putInt("ClimateTargetTenthsC", climateTargetTenthsC);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);

        hotFuel = clampFuel(input.getIntOr("HotFuel", 0));
        coldFuel = clampFuel(input.getIntOr("ColdFuel", 0));
        hotFluidStorage.syncFromFuel(hotFuel);
        coldFluidStorage.syncFromFuel(coldFuel);

        ticksExisted = Math.max(0, input.getIntOr("TicksExisted", 0));
        insulationLevel = Math.max(0, Math.min(WARM_UP_TIME, input.getIntOr("InsulationLevel", 0)));

        climateControlEnabled = input.getBooleanOr("ClimateControlEnabled", false);
        climateTargetTenthsC = Math.max(
                MIN_CLIMATE_TARGET_TENTHS_C,
                Math.min(
                        MAX_CLIMATE_TARGET_TENTHS_C,
                        input.getIntOr("ClimateTargetTenthsC", DEFAULT_CLIMATE_TARGET_TENTHS_C)
                )
        );
        thermostatMode = 0;
    }
}
