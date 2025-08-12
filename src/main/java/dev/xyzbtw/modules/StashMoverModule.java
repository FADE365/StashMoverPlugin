package dev.xyzbtw.modules;

import dev.xyzbtw.StashMoverAddon;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

public class StashMoverModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Mode {
        MOVER,
        LOADER,
        CHAMBER
    }

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .defaultValue(Mode.MOVER)
        .build());

    private final Setting<Integer> chestDelay = sgGeneral.add(new IntSetting.Builder()
        .name("chest-delay")
        .description("Delay between chest clicks.")
        .defaultValue(1)
        .min(0)
        .max(10)
        .visible(() -> mode.get() == Mode.MOVER)
        .build());

    private final Setting<Integer> chestDistance = sgGeneral.add(new IntSetting.Builder()
        .name("chest-distance")
        .description("Distance between you and lootchests.")
        .defaultValue(100)
        .min(10)
        .max(1000)
        .visible(() -> mode.get() == Mode.MOVER)
        .build());

    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disable")
        .description("Disables when chest or inventory conditions are met.")
        .defaultValue(true)
        .visible(() -> mode.get() != Mode.CHAMBER)
        .build());

    private final Setting<Boolean> useEchest = sgGeneral.add(new BoolSetting.Builder()
        .name("use-echest")
        .description("Uses ender chest.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.MOVER)
        .build());

    private final Setting<Boolean> ignoreSingular = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-single-chest")
        .description("Doesn't steal from single chests.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.MOVER)
        .build());

    public final Setting<Boolean> onlyShulkers = sgGeneral.add(new BoolSetting.Builder()
        .name("only-shulkers")
        .description("Only moves shulkers")
        .defaultValue(false)
        .visible(() -> mode.get() != Mode.CHAMBER)
        .build());

    public final Setting<Boolean> censorCoordinates = sgGeneral.add(new BoolSetting.Builder()
        .name("censor-coords")
        .description("Censors your coords in chat")
        .defaultValue(false)
        .build());

    private final Setting<String> otherIGN = sgGeneral.add(new StringSetting.Builder()
        .name("other-ign")
        .description("The username of the other person that's moving stash")
        .defaultValue("xyzbtwballs")
        .build());

    private final Setting<String> loadMessage = sgGeneral.add(new StringSetting.Builder()
        .name("load-message")
        .description("The message that both accounts use.")
        .defaultValue("LOAD PEARL")
        .build());

    private final Setting<BlockPos> pearlChest = sgGeneral.add(new BlockPosSetting.Builder()
        .name("pearl-chest")
        .description("Chest that contains pearls to throw.")
        .defaultValue(BlockPos.ORIGIN)
        .visible(() -> mode.get() == Mode.CHAMBER)
        .build());

    private final Setting<Integer> pearlDelay = sgGeneral.add(new IntSetting.Builder()
        .name("pearl-delay")
        .description("Ticks to wait before throwing the pearl.")
        .defaultValue(20)
        .min(0)
        .max(100)
        .visible(() -> mode.get() == Mode.CHAMBER)
        .build());

    // Internal states
    private enum MoverStage {
        LOOT,
        WALKING_TO_CHEST,
        ECHEST_LOOT
    }

    private enum LoaderStage {
        WAITING
    }

    private MoverStage moverStage = MoverStage.LOOT;
    private LoaderStage loaderStage = LoaderStage.WAITING;

    private enum ChamberStage {
        IDLE,
        LOADING,
        THROWING,
        WAITING
    }

    private ChamberStage chamberStage = ChamberStage.IDLE;
    private int pearlTimer = 0;

    private BlockPos currentChest;
    private BlockPos echestPos;
    private final Set<BlockPos> lootedChests = new HashSet<>();
    private int chestTickTimer = 0;
    private BlockPos lastChestPos;

    public StashMoverModule() {
        super(StashMoverAddon.CATEGORY, "stash-mover", "Moves stashes with pearls");
    }

    @Override
    public void onActivate() {
        moverStage = MoverStage.LOOT;
        loaderStage = LoaderStage.WAITING;
        chamberStage = ChamberStage.IDLE;
        pearlTimer = 0;
        currentChest = null;
        echestPos = null;
        lastChestPos = null;
        lootedChests.clear();
        chestTickTimer = 0;
    }

    @Override
    public void onDeactivate() {
        currentChest = null;
        echestPos = null;
        lastChestPos = null;
        lootedChests.clear();
        chestTickTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        chestTickTimer++;

        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler container) {
            if (chestTickTimer >= chestDelay.get()) {
                if (mode.get() == Mode.MOVER) {
                    if (moverStage == MoverStage.ECHEST_LOOT) dumpInventory(container);
                    else lootChest(container);
                }
                else if (mode.get() == Mode.LOADER) loadChest(container);
                else if (mode.get() == Mode.CHAMBER) grabPearl(container);
                chestTickTimer = 0;
            }
            return;
        }

        if (mode.get() == Mode.MOVER) {
            if (moverStage == MoverStage.ECHEST_LOOT) {
                if (isPlayerInventoryEmpty()) {
                    moverStage = MoverStage.LOOT;
                    return;
                }
                if (echestPos == null) {
                    echestPos = findEnderChest();
                }
                if (echestPos != null) {
                    double distSq = echestPos.getSquaredDistance(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                    if (distSq <= 25) {
                        openChest(echestPos);
                        echestPos = null;
                    }
                }
                return;
            }

            if (isPlayerInventoryFull()) {
                if (useEchest.get()) {
                    moverStage = MoverStage.ECHEST_LOOT;
                    currentChest = null;
                    return;
                } else if (autoDisable.get()) {
                    toggle();
                    return;
                }
            }

            if (currentChest == null) {
                currentChest = findChest(true, ignoreSingular.get());
                if (currentChest != null) moverStage = MoverStage.WALKING_TO_CHEST;
            }

            if (currentChest != null) {
                double distSq = currentChest.getSquaredDistance(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                if (distSq <= 25) {
                    openChest(currentChest);
                    lastChestPos = currentChest;
                    currentChest = null;
                    moverStage = MoverStage.LOOT;
                }
            }
        } else if (mode.get() == Mode.LOADER) {
            if (currentChest == null) {
                currentChest = findChest(false, false);
            }

            if (currentChest != null) {
                double distSq = currentChest.getSquaredDistance(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                if (distSq <= 25) {
                    openChest(currentChest);
                    currentChest = null;
                }
            }
        } else if (mode.get() == Mode.CHAMBER) {
            switch (chamberStage) {
                case IDLE -> {
                    if (!hasPearl()) {
                        BlockPos pearlPos = pearlChest.get();
                        double distSq = pearlPos.getSquaredDistance(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                        if (distSq <= 25) {
                            openChest(pearlPos);
                            chamberStage = ChamberStage.LOADING;
                        }
                    } else {
                        chamberStage = ChamberStage.THROWING;
                        pearlTimer = pearlDelay.get();
                    }
                }
                case THROWING -> {
                    if (pearlTimer-- <= 0) {
                        int slot = findPearlSlot();
                        if (slot != -1) {
                            mc.player.getInventory().selectedSlot = slot;
                            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                        }
                        chamberStage = ChamberStage.WAITING;
                    }
                }
                case WAITING -> {
                    // Wait until the thrown pearl finishes teleporting
                }
                default -> {
                }
            }
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mc.player == null) return;

        String text = event.getMessage().getString();
        String ign = otherIGN.get();
        String trigger = loadMessage.get();

        if (text.contains(trigger) && text.contains(ign)) {
            String coords;
            if (censorCoordinates.get()) {
                coords = "[CENSORED]";
            } else {
                coords = String.format("X: %d Y: %d Z: %d", mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
            }

            mc.player.networkHandler.sendChatMessage("/msg " + ign + " " + coords);
        }
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (mode.get() != Mode.CHAMBER) return;
        if (event.entity instanceof EnderPearlEntity pearl && pearl.getOwner() == mc.player) {
            chamberStage = ChamberStage.WAITING;
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (mode.get() != Mode.CHAMBER) return;
        if (event.entity instanceof EnderPearlEntity pearl && pearl.getOwner() == mc.player) {
            chamberStage = ChamberStage.IDLE;
        }
    }

    private BlockPos findChest(boolean skipLooted, boolean skipSingle) {
        BlockPos playerPos = mc.player.getBlockPos();
        int range = Math.min(chestDistance.get(), 32); // limit search to avoid heavy loops
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        BlockPos closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    mutable.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);

                    if (skipLooted && lootedChests.contains(mutable)) continue;

                    BlockState state = mc.world.getBlockState(mutable);
                    if (state.getBlock() instanceof ChestBlock) {
                        if (skipSingle && state.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) continue;

                        double distSq = mutable.getSquaredDistance(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                        if (distSq < closestDistSq) {
                            closestDistSq = distSq;
                            closest = mutable.toImmutable();
                        }
                    }
                }
            }
        }

        return closest;
    }

    private BlockPos findEnderChest() {
        BlockPos playerPos = mc.player.getBlockPos();
        int range = Math.min(chestDistance.get(), 32);
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        BlockPos closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    mutable.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);

                    if (mc.world.getBlockState(mutable).isOf(Blocks.ENDER_CHEST)) {
                        double distSq = mutable.getSquaredDistance(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                        if (distSq < closestDistSq) {
                            closestDistSq = distSq;
                            closest = mutable.toImmutable();
                        }
                    }
                }
            }
        }

        return closest;
    }

    private void openChest(BlockPos pos) {
        if (mc.player == null || mc.interactionManager == null) return;
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
    }

    private void grabPearl(GenericContainerScreenHandler container) {
        int invSize = container.getInventory().size();
        for (int i = 0; i < invSize; i++) {
            ItemStack stack = container.getSlot(i).getStack();
            if (stack.isOf(Items.ENDER_PEARL)) {
                mc.interactionManager.clickSlot(container.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                mc.player.closeHandledScreen();
                chamberStage = ChamberStage.THROWING;
                pearlTimer = pearlDelay.get();
                break;
            }
        }
    }

    private boolean hasPearl() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) return true;
        }
        return false;
    }

    private int findPearlSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.ENDER_PEARL)) return i;
        }
        return -1;
    }

    private void lootChest(GenericContainerScreenHandler container) {
        int invSize = container.getInventory().size();
        for (int i = 0; i < invSize; i++) {
            ItemStack stack = container.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            if (onlyShulkers.get()) {
                if (!(stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock)) continue;
            }
            mc.interactionManager.clickSlot(container.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            break;
        }

        if (isChestEmpty(container)) {
            if (lastChestPos != null) lootedChests.add(lastChestPos);
            mc.player.closeHandledScreen();
            if (autoDisable.get()) toggle();
        }
    }

    private void loadChest(GenericContainerScreenHandler container) {
        int invSize = container.getInventory().size();
        int totalSlots = container.slots.size();
        for (int i = invSize; i < totalSlots; i++) {
            ItemStack stack = container.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            if (onlyShulkers.get()) {
                if (!(stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock))
                    continue;
            }
            mc.interactionManager.clickSlot(container.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            break;
        }

        if (isInventoryEmpty(container)) {
            mc.player.closeHandledScreen();
            if (autoDisable.get()) toggle();
        }
    }

    private void dumpInventory(GenericContainerScreenHandler container) {
        int invSize = container.getInventory().size();
        int totalSlots = container.slots.size();
        for (int i = invSize; i < totalSlots; i++) {
            ItemStack stack = container.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            if (onlyShulkers.get()) {
                if (!(stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock))
                    continue;
            }
            mc.interactionManager.clickSlot(container.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            break;
        }

        if (isInventoryEmpty(container)) {
            mc.player.closeHandledScreen();
            moverStage = MoverStage.LOOT;
        }
    }

    private boolean isChestEmpty(GenericContainerScreenHandler container) {
        int invSize = container.getInventory().size();
        for (int i = 0; i < invSize; i++) {
            ItemStack stack = container.getSlot(i).getStack();
            if (!stack.isEmpty()) {
                if (onlyShulkers.get()) {
                    if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock) return false;
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isInventoryEmpty(GenericContainerScreenHandler container) {
        int invSize = container.getInventory().size();
        int totalSlots = container.slots.size();
        for (int i = invSize; i < totalSlots; i++) {
            ItemStack stack = container.getSlot(i).getStack();
            if (!stack.isEmpty()) {
                if (onlyShulkers.get()) {
                    if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock)
                        return false;
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isPlayerInventoryFull() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return false;
        }
        return true;
    }

    private boolean isPlayerInventoryEmpty() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) {
                if (onlyShulkers.get()) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock)
                        return false;
                } else {
                    return false;
                }
            }
        }
        return true;
    }
}
