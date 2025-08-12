package dev.xyzbtw;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

import dev.xyzbtw.modules.StashMoverModule;

public class StashMoverAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("StashMover");

    @Override
    public void onInitialize() {
        LOG.info("Initializing StashMover Meteor Addon");

        // Modules
        Modules.get().add(new StashMoverModule());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "dev.xyzbtw";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("xyzbtw", "StashMoverAddon");
    }
}
