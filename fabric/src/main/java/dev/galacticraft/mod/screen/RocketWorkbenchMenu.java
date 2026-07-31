/*
 * Copyright (c) 2019-2026 Team Galacticraft
 * Copyright (c) 2026 Colin Vaughn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dev.galacticraft.mod.screen;

import com.mojang.datafixers.util.Pair;
import dev.galacticraft.api.accessor.ResearchAccessor;
import dev.galacticraft.api.accessor.ServerResearchAccessor;
import dev.galacticraft.api.component.GCDataComponents;
import dev.galacticraft.api.inventory.MirroredSlot;
import dev.galacticraft.api.item.Schematic;
import dev.galacticraft.api.rocket.RocketData;
import dev.galacticraft.api.rocket.RocketPrefabs;
import dev.galacticraft.api.rocket.part.RocketPart;
import dev.galacticraft.api.rocket.part.RocketUpgrade;
import dev.galacticraft.mod.Constant;
import dev.galacticraft.mod.content.GCRocketParts;
import dev.galacticraft.mod.content.block.entity.RocketWorkbenchBlockEntity;
import dev.galacticraft.mod.content.entity.vehicle.Buggy;
import dev.galacticraft.mod.content.item.GCItems;
import dev.galacticraft.mod.content.rocket.part.data.ExplosiveRocketData;
import dev.galacticraft.mod.content.rocket.part.data.RocketUpgradeData;
import dev.galacticraft.mod.content.rocket.part.data.StorageRocketData;
import dev.galacticraft.mod.content.advancements.GCTriggers;
import dev.galacticraft.mod.machine.storage.VariableSizedContainer;
import dev.galacticraft.mod.machine.workbench.WorkbenchLayout;
import dev.galacticraft.mod.machine.workbench.WorkbenchPage;
import dev.galacticraft.mod.machine.workbench.WorkbenchPageDisplay;
import dev.galacticraft.mod.machine.workbench.WorkbenchPages;
import dev.galacticraft.mod.machine.workbench.WorkbenchSlot;
import dev.galacticraft.mod.recipe.RocketRecipe;
import dev.galacticraft.mod.recipe.WorkbenchRecipe;
import dev.galacticraft.mod.tag.GCItemTags;
import dev.galacticraft.mod.world.inventory.RocketResultSlot;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.TntBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static dev.galacticraft.mod.Constant.RocketWorkbench.*;

/**
 * The rocket workbench, which is a flip-book of schematic pages as legacy Galacticraft's NASA
 * workbench was.
 *
 * <p>Each page is one recipe and lays out its own slots, so the menu is rebuilt - by reopening -
 * whenever the player turns a page. The last page is always the unlock page, where a schematic is
 * consumed to reveal the page it belongs to.
 */
public class RocketWorkbenchMenu extends AbstractContainerMenu implements VariableSizedContainer.Listener, ContainerListener {
    public static final int BUTTON_BACK = 0;
    public static final int BUTTON_NEXT = 1;
    public static final int BUTTON_UNLOCK = 2;

    public final RocketWorkbenchBlockEntity workbench;
    public final Inventory playerInventory;

    private final List<WorkbenchPage> pages;
    private final WorkbenchPage page;
    private final @Nullable RecipeHolder<? extends WorkbenchRecipe> recipe;
    private final WorkbenchPageDisplay display;

    private List<Component> missingResearch = List.of();

    private boolean coneComplete;
    private boolean bodyComplete;
    private boolean boostersComplete;
    private boolean finsComplete;
    private boolean engineComplete;

    private Slot coneSlot;
    private List<Slot> bodySlots = List.of();
    private List<Slot> boosterSlots = List.of();
    private List<Slot> finSlots = List.of();
    private Slot engineSlot;

    public RocketWorkbenchMenu(int syncId, RocketWorkbenchBlockEntity workbench, Inventory playerInventory) {
        this(syncId, workbench, playerInventory, null);
    }

    public RocketWorkbenchMenu(int syncId, RocketWorkbenchBlockEntity workbench, Inventory playerInventory, @Nullable ResourceLocation pageId) {
        super(GCMenuTypes.ROCKET_WORKBENCH, syncId);
        this.playerInventory = playerInventory;
        this.workbench = workbench;

        // Add client-side listeners via RocketWorkbenchScreen
        if (this.workbench.getLevel() instanceof ServerLevel) {
            this.workbench.ingredients.addListener(this);
            this.workbench.chests.addListener(this);
        }

        this.pages = pagesIncluding(playerInventory.player, pageId);
        this.page = resolvePage(this.pages, pageId);
        this.recipe = this.page.id().equals(WorkbenchPages.ADD_SCHEMATIC_ID)
                ? null
                : WorkbenchPages.recipe(playerInventory.player.level(), this.page.id());
        this.display = this.recipe != null ? this.recipe.value().display() : WorkbenchPageDisplay.addSchematic();

        // The unlock page has no ingredient wells; resizing to zero there would tip a half-built
        // rocket onto the floor just for looking at the schematic page.
        if (this.recipe != null) {
            this.workbench.resizeInventory(this.ingredientCount());
        }
        this.addSlots();
        this.returnIncompatibleIngredients();
        this.onItemChanged();
    }

    public RocketWorkbenchMenu(int syncId, Inventory playerInventory, OpeningData data) {
        this(syncId, (RocketWorkbenchBlockEntity) playerInventory.player.level().getBlockEntity(data.pos), playerInventory, data.page.orElse(null));
    }

    /**
     * The player's pages, plus {@code pageId} itself if it names a real recipe the player is not
     * shown yet.
     *
     * <p>The server picks the page and both sides lay their slots out from it. A client whose
     * research has not caught up would otherwise build a different page from the server, and its
     * slot indices would no longer line up - items would appear in the wrong wells. Trusting the
     * requested page keeps the two in step; the server is still the one that decides what opens.
     */
    private static List<WorkbenchPage> pagesIncluding(Player player, @Nullable ResourceLocation pageId) {
        List<WorkbenchPage> pages = WorkbenchPages.visible(player.level(), player);
        if (pageId == null || pages.stream().anyMatch(page -> page.id().equals(pageId))) {
            return pages;
        }

        RecipeHolder<? extends WorkbenchRecipe> requested = WorkbenchPages.recipe(player.level(), pageId);
        if (requested == null) {
            return pages;
        }

        List<WorkbenchPage> candidates = new ArrayList<>(pages);
        candidates.removeIf(page -> page.id().equals(WorkbenchPages.ADD_SCHEMATIC_ID));
        candidates.add(WorkbenchPages.page(requested));
        return WorkbenchPages.order(candidates, id -> true);
    }

    /**
     * Falls back to the first page the player can see. A page can vanish between opening and
     * reopening - an operator revoking research, or a datapack reload - and legacy always had page 0
     * to fall back on.
     */
    private static WorkbenchPage resolvePage(List<WorkbenchPage> pages, @Nullable ResourceLocation pageId) {
        if (pageId != null) {
            for (WorkbenchPage page : pages) {
                if (page.id().equals(pageId)) return page;
            }
        }
        return pages.getFirst();
    }

    private int ingredientCount() {
        return this.recipe == null ? 0 : this.recipe.value().ingredientSlots().size();
    }

    public WorkbenchPage page() {
        return this.page;
    }

    public List<WorkbenchPage> pages() {
        return this.pages;
    }

    public WorkbenchPageDisplay display() {
        return this.display;
    }

    /**
     * @param direction {@code 1} for the next page, {@code -1} for the previous
     * @return the page that button reaches, or null if this is already the end of the book
     */
    public @Nullable WorkbenchPage adjacentPage(int direction) {
        int index = this.pages.indexOf(this.page) + direction;
        return index >= 0 && index < this.pages.size() ? this.pages.get(index) : null;
    }

    protected void addSlots() {
        if (this.recipe != null) {
            this.addIngredientSlots(this.recipe.value());
        } else {
            this.addSlot(new FilteredSlot(this.workbench.schematic, 0, ADD_SCHEMATIC_SLOT_X, ADD_SCHEMATIC_SLOT_Y,
                    stack -> stack.getItem() instanceof Schematic
                            && WorkbenchPages.pageUnlockedBy(this.playerInventory.player.level(), stack.getItem()) != null));
        }

        List<WorkbenchLayout.Position> chests = this.display.chestSlots();
        for (int chest = 0; chest < chests.size() && chest < RocketWorkbenchBlockEntity.CHEST_SLOTS; ++chest) {
            final int index = chest;
            this.addSlot(new FilteredSlot(this.workbench.chests, index, chests.get(chest).x(), chests.get(chest).y(),
                    stack -> this.workbench.chests.canPlaceItem(index, stack)).withBackground(Constant.SlotSprite.CHEST));
        }

        if (this.display.resultSlot() != null) {
            this.addSlot(new RocketResultSlot(this, this.workbench.output, 0, this.display.resultSlot().x(), this.display.resultSlot().y()));
        }

        for (int row = 0; row < 3; ++row) {
            for (int column = 0; column < 9; ++column) {
                this.addSlot(new Slot(this.playerInventory, column + row * 9 + 9, column * 18 + 8, row * 18 + this.display.playerInventoryY()));
            }
        }

        for (int column = 0; column < 9; ++column) {
            this.addSlot(new Slot(this.playerInventory, column, column * 18 + 8, this.display.hotbarY()));
        }
    }

    /**
     * Hands back any part the page just turned to will not accept. Without this a tier-1 plate left
     * over from another page sits in a tier-2 well that refuses to take it, silently blocking a
     * build the player cannot see anything wrong with.
     */
    private void returnIncompatibleIngredients() {
        if (this.recipe == null || !(this.workbench.getLevel() instanceof ServerLevel)) return;

        for (Slot slot : this.slots) {
            if (slot.container != this.workbench.ingredients) continue;

            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && !slot.mayPlace(stack)) {
                slot.set(ItemStack.EMPTY);
                if (!this.playerInventory.add(stack)) {
                    this.playerInventory.player.drop(stack, false);
                }
            }
        }
    }

    private void addIngredientSlots(WorkbenchRecipe recipe) {
        List<WorkbenchSlot> slots = recipe.ingredientSlots();
        List<RocketRecipe.RocketSlotData> rocketSlots = recipe instanceof RocketRecipe rocket
                ? RocketRecipe.slotData(rocket.bodyHeight(), !rocket.boosters().isEmpty())
                : List.of();

        List<Slot> body = new ArrayList<>();
        List<Slot> boosters = new ArrayList<>();
        List<Slot> fins = new ArrayList<>();

        for (int index = 0; index < slots.size(); index++) {
            WorkbenchSlot data = slots.get(index);
            Predicate<ItemStack> filter = data.ingredient()::test;
            FilteredSlot slot = data.mirrored()
                    ? new MirroredFilteredSlot(this.workbench.ingredients, index, data.x(), data.y(), filter)
                    : new FilteredSlot(this.workbench.ingredients, index, data.x(), data.y(), filter);
            if (data.background() != null) slot.withBackground(data.background());
            this.addSlot(slot);

            if (index < rocketSlots.size()) {
                switch (rocketSlots.get(index).partType()) {
                    case CONE -> this.coneSlot = slot;
                    case BODY -> body.add(slot);
                    case BOOSTER -> boosters.add(slot);
                    case FIN -> fins.add(slot);
                    case ENGINE -> this.engineSlot = slot;
                    default -> { }
                }
            }
        }

        this.bodySlots = body;
        this.boosterSlots = boosters;
        this.finSlots = fins;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return switch (id) {
            case BUTTON_BACK -> this.flip(player, -1);
            case BUTTON_NEXT -> this.flip(player, 1);
            case BUTTON_UNLOCK -> this.unlockSchematic(player);
            default -> false;
        };
    }

    private boolean flip(Player player, int direction) {
        WorkbenchPage target = this.adjacentPage(direction);
        if (target == null) return false;

        this.openPage(player, target.id());
        return true;
    }

    /**
     * Legacy's {@code S_UNLOCK_NEW_SCHEMATIC}: read the unlock slot, record the page against the
     * player and spend the schematic. Refuses anything that unlocks nothing, so a misplaced item is
     * never destroyed.
     */
    private boolean unlockSchematic(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;

        ItemStack stack = this.workbench.schematic.getItem(0);
        if (!(stack.getItem() instanceof Schematic)) return false;

        Item schematic = stack.getItem();
        RecipeHolder<? extends WorkbenchRecipe> unlocked = WorkbenchPages.pageUnlockedBy(player.level(), schematic);
        if (unlocked == null) return false;

        ResourceLocation unlockId = WorkbenchPages.unlockId(BuiltInRegistries.ITEM.getKey(schematic));
        if (((ResearchAccessor) player).galacticraft$isUnlocked(unlockId)) {
            // Creative players are considered to know every page, and survival players may insert
            // a second copy of a schematic they already spent. In either case the button should
            // still turn to that schematic's page instead of appearing to do nothing.
            this.openPage(player, unlocked.id());
            return true;
        }

        ((ServerResearchAccessor) serverPlayer).galacticraft$unlockRocketPartRecipes(unlockId);
        GCTriggers.UNLOCK_SCHEMATIC.trigger(serverPlayer, stack);
        stack.shrink(1);
        this.workbench.schematic.setItem(0, stack);
        this.workbench.schematic.setChanged();

        this.openPage(player, unlocked.id());
        return true;
    }

    /** Reopening keeps the client's slot list in step with the server's; legacy reopened too. */
    private void openPage(Player player, ResourceLocation pageId) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            dev.architectury.registry.menu.MenuRegistry.openExtendedMenu(serverPlayer, this.workbench.menuOn(pageId));
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        // Remove client-side listeners via RocketWorkbenchScreen
        if (this.workbench.getLevel() instanceof ServerLevel) {
            this.workbench.ingredients.removeListener(this);
            this.workbench.chests.removeListener(this);
        }
    }

    protected boolean isIngredient(ItemStack stack) {
        if (this.recipe == null) return false;
        return this.recipe.value().getIngredients().stream().distinct().anyMatch(ingredient -> ingredient.test(stack));
    }

    protected boolean isWorkbenchInventory(int slotIndex) {
        return slotIndex < this.slots.size() - 9 * 4;
    }

    public int getRecipeSize() {
        return this.ingredientCount();
    }

    /** @return whether {@code recipe} is the build page this menu currently has open. */
    public boolean isCurrentRecipe(WorkbenchRecipe recipe) {
        return this.recipe != null && this.recipe.value() == recipe;
    }

    /**
     * The page's ingredient wells, in slot order. Recipe viewers fill these when transferring a
     * recipe, and must not stray into the upgrade wells beside them.
     */
    public List<Slot> ingredientSlots() {
        return this.slots.stream().filter(slot -> slot.container == this.workbench.ingredients).toList();
    }

    /** @return the slot the crafted item appears in, or null on a page that crafts nothing. */
    public @Nullable Slot resultSlot() {
        return this.slots.stream().filter(slot -> slot.container == this.workbench.output).findFirst().orElse(null);
    }

    /** The first of the player's own 36 slots. */
    public int firstPlayerSlot() {
        return this.slots.size() - 9 * 4;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(Player player, int index) {
        ItemStack out = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        ItemStack stack = slot.getItem();
        if (!stack.isEmpty()) {
            out = stack.copy();
            int slots = this.slots.size();
            if (isWorkbenchInventory(index)) {
                if (!this.moveItemStackTo(stack, slots - 9 * 4, slots, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (this.isIngredient(stack)) {
                    if (!this.moveItemStackTo(stack, 0, this.ingredientCount(), false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(stack, 0, slots - 9 * 4, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            slot.onTake(player, stack);
        }

        return out;
    }

    private RocketData withWorkbenchUpgrade(RocketData base) {
        ItemStack upgradeStack = this.workbench.chests.getItem(0);

        Optional<EitherHolder<RocketUpgrade<?, ?>>> upgrade = Optional.empty();
        Optional<RocketUpgradeData> upgradeData = Optional.empty();

        if (upgradeStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof TntBlock tntBlock) {
            upgrade = Optional.of(new EitherHolder<>(GCRocketParts.EXPLOSIVE_UPGRADE));
            upgradeData = Optional.of(new ExplosiveRocketData(BuiltInRegistries.BLOCK.getKey(tntBlock)));
        } else {
            // Chests may go in any of the three slots; the rocket's cargo scales with how many are installed.
            int chests = this.installedChests();

            if (chests > 0) {
                upgrade = Optional.of(new EitherHolder<>(GCRocketParts.STORAGE_UPGRADE));
                upgradeData = Optional.of(new StorageRocketData(chests));
            }
        }

        return new RocketData(
                base.cone(),
                base.body(),
                base.fin(),
                base.booster(),
                base.engine(),
                upgrade,
                upgradeData,
                base.color()
        );
    }

    private int installedChests() {
        int chests = 0;
        for (int slot = 0; slot < this.workbench.chests.getContainerSize(); ++slot) {
            if (this.workbench.chests.getItem(slot).is(GCItemTags.ROCKET_STORAGE_UPGRADE_ITEMS)) {
                ++chests;
            }
        }
        return chests;
    }

    public RocketData previewRocket() {
        RocketData data = this.recipe != null
                ? this.recipe.value().getResultItem(this.registries()).getOrDefault(GCDataComponents.ROCKET_DATA, RocketPrefabs.TIER_1)
                : RocketPrefabs.TIER_1;
        RocketData withParts = new RocketData(
                this.coneComplete ? data.cone() : Optional.empty(),
                this.bodyComplete ? data.body() : Optional.empty(),
                this.finsComplete ? data.fin() : Optional.empty(),
                this.boostersComplete ? data.booster() : Optional.empty(),
                this.engineComplete ? data.engine() : Optional.empty(),
                data.upgrade(),
                data.upgradeData(),
                data.color()
        );
        return this.withWorkbenchUpgrade(withParts);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this.workbench, player);
    }

    @Override
    public void onSizeChanged() {
        this.onItemChanged();
    }

    @Override
    public void onItemChanged() {
        boolean matches = this.recipe != null && this.recipe.value().matches(this.workbench.ingredients.asInput(), this.workbench.getLevel());

        // Worked out on both sides: an empty result slot otherwise gives the player no clue that
        // research, rather than a missing part, is what is blocking the build.
        this.missingResearch = matches ? this.lockedParts(this.resultRocketData()) : List.of();

        // The server owns the result slot. Recomputing it on the client can erase the
        // synchronized result when research data has not arrived yet after login.
        if (this.workbench.getLevel() instanceof ServerLevel) {
            if (matches && this.missingResearch.isEmpty()) {
                this.workbench.output.setItem(0, this.buildResult());
            } else {
                this.workbench.output.clearContent();
            }
        }

        this.coneComplete = this.slotMatchesRecipe(this.coneSlot);
        this.bodyComplete = this.bodySlots.stream().allMatch(this::slotMatchesRecipe);
        this.boostersComplete = this.boosterSlots.stream().allMatch(this::slotMatchesRecipe);
        this.finsComplete = this.finSlots.stream().allMatch(this::slotMatchesRecipe);
        this.engineComplete = this.slotMatchesRecipe(this.engineSlot);
    }

    private HolderLookup.Provider registries() {
        return this.playerInventory.player.level().registryAccess();
    }

    /**
     * The rocket this page builds, or null if it builds something else. A buggy or an astro miner
     * carries no rocket data, and must not be gated on rocket-part research it has nothing to do
     * with - defaulting to tier-1 data here would block those pages for anyone who has not
     * researched a tier-1 rocket.
     */
    private @Nullable RocketData resultRocketData() {
        if (this.recipe == null) return null;
        return this.recipe.value().getResultItem(this.registries()).get(GCDataComponents.ROCKET_DATA);
    }

    /** Applies the page's upgrade wells to the crafted item: rocket cargo, or a buggy's storage. */
    private ItemStack buildResult() {
        ItemStack result = this.recipe.value().getResultItem(this.registries()).copy();

        if (result.has(GCDataComponents.ROCKET_DATA)) {
            result.set(GCDataComponents.ROCKET_DATA, this.withWorkbenchUpgrade(result.get(GCDataComponents.ROCKET_DATA)));
        } else if (result.is(GCItems.BUGGY)) {
            // Legacy's buggy bench read its addon wells as the buggy's storage tier.
            int storage = 0;
            for (int slot = 0; slot < this.workbench.chests.getContainerSize(); ++slot) {
                if (this.workbench.chests.getItem(slot).is(GCItems.BUGGY_STORAGE)) ++storage;
            }
            result.set(GCDataComponents.BUGGY_TYPE, Math.min(storage, Buggy.BuggyType.STORAGE_36.getId()));
        }

        return result;
    }

    private boolean slotMatchesRecipe(Slot slot) {
        if (slot == null || this.recipe == null) return false;
        List<WorkbenchSlot> slots = this.recipe.value().ingredientSlots();
        int index = slot.getContainerSlot();
        return index < slots.size() && slots.get(index).ingredient().test(slot.getItem());
    }

    /**
     * Names the structural parts of {@code data} the player has not researched yet. Both sides can
     * work this out - the client keeps its own copy of the player's unlocks - so the screen can
     * explain an empty result slot without asking the server.
     */
    private List<Component> lockedParts(@Nullable RocketData data) {
        Player player = this.playerInventory.player;
        if (data == null || player == null) {
            return List.of();
        }

        ResearchAccessor research = (ResearchAccessor) player;
        HolderLookup.Provider lookup = player.level().registryAccess();
        List<Component> locked = new ArrayList<>();
        addIfLocked(locked, research, lookup, data.cone());
        addIfLocked(locked, research, lookup, data.body());
        addIfLocked(locked, research, lookup, data.fin());
        addIfLocked(locked, research, lookup, data.booster());
        addIfLocked(locked, research, lookup, data.engine());
        return locked;
    }

    private static <T extends RocketPart<?, ?>> void addIfLocked(List<Component> locked, ResearchAccessor research, HolderLookup.Provider lookup, Optional<EitherHolder<T>> part) {
        if (part.isEmpty()) {
            return;
        }
        ResourceKey<T> key = part.get().unwrap(lookup).flatMap(Holder::unwrapKey).orElse(null);
        // Fail open if the registry lookup is missing; the server still validates the output.
        if (key != null && !research.galacticraft$isUnlocked(GCRocketParts.recipeId(key))) {
            locked.add(RocketPart.getName(key));
        }
    }

    /**
     * @return the parts blocking the current assembly, or empty if research is not what is stopping it.
     */
    public List<Component> getMissingResearch() {
        return this.missingResearch;
    }

    @Override
    public void containerChanged(Container sender) {
        this.onItemChanged();
    }

    private static class FilteredSlot extends Slot {
        private final Predicate<ItemStack> filter;
        private Pair<ResourceLocation, ResourceLocation> background;

        public FilteredSlot(Container container, int slot, int x, int y, Predicate<ItemStack> filter) {
            super(container, slot, x, y);
            this.filter = filter;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.isEmpty() || this.filter.test(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public @Nullable Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return this.background;
        }

        public FilteredSlot withBackground(ResourceLocation background) {
            this.background = Pair.of(InventoryMenu.BLOCK_ATLAS, background);
            return this;
        }
    }

    private static class MirroredFilteredSlot extends FilteredSlot implements MirroredSlot {
        public MirroredFilteredSlot(Container container, int slot, int x, int y, Predicate<ItemStack> filter) {
            super(container, slot, x, y, filter);
        }
    }

    public record OpeningData(BlockPos pos, Optional<ResourceLocation> page) {
        public static final StreamCodec<ByteBuf, OpeningData> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpeningData::pos,
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), OpeningData::page,
                OpeningData::new
        );
    }
}
