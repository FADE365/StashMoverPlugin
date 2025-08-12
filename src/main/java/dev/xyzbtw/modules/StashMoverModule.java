package dev.xyzbtw.modules;

import dev.xyzbtw.StashMoverAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.orbit.EventHandler;

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
        .description("If lootchest is full.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.MOVER)
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
        .description("Only steals shulkers")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.MOVER)
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

    public StashMoverModule() {
        super(StashMoverAddon.CATEGORY, "stash-mover", "Moves stashes with pearls");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // TODO: implement stash moving logic
    }
}
