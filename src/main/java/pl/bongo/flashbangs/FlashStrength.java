package pl.bongo.flashbangs;

import net.minecraft.resources.Identifier;

/** Ordered by severity; each profile owns two chains so a new flash gets fresh GPU history. */
public enum FlashStrength {
    WEAK("weak", "flash_weak", 10),
    MEDIUM("medium", "flash_medium", 20),
    FULL("full", "flash", 60);

    private final String commandName;
    private final Identifier effect;
    private final Identifier alternate;
    private final int durationTicks;

    FlashStrength(String commandName, String path, int durationTicks) {
        this.commandName = commandName;
        this.effect = Identifier.fromNamespaceAndPath("bongoflashbangs", path);
        this.alternate = Identifier.fromNamespaceAndPath("bongoflashbangs", path + "_alt");
        this.durationTicks = durationTicks;
    }

    public String commandName() { return commandName; }
    public Identifier effect(boolean useAlternate) { return useAlternate ? alternate : effect; }
    public int durationTicks() { return durationTicks; }
}
