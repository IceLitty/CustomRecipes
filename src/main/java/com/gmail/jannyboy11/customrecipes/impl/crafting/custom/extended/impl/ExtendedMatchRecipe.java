package com.gmail.jannyboy11.customrecipes.impl.crafting.custom.extended.impl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Function;

import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftNamespacedKey;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import com.gmail.jannyboy11.customrecipes.api.crafting.CraftingRecipe;
import com.gmail.jannyboy11.customrecipes.impl.crafting.custom.extended.ExtendedCraftingIngredient;
import com.gmail.jannyboy11.customrecipes.impl.crafting.custom.extended.ExtendedCraftingRecipe;
import com.gmail.jannyboy11.customrecipes.impl.crafting.custom.extended.ExtendedShapedRecipe;
import com.gmail.jannyboy11.customrecipes.impl.crafting.custom.extended.ExtendedShapelessRecipe;
import com.gmail.jannyboy11.customrecipes.impl.crafting.custom.extended.MatchStrategy;
import com.gmail.jannyboy11.customrecipes.impl.crafting.custom.extended.Shape;

import net.minecraft.server.v1_12_R1.InventoryCrafting;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.MinecraftKey;
import net.minecraft.server.v1_12_R1.NonNullList;
import net.minecraft.server.v1_12_R1.World;

public class ExtendedMatchRecipe implements ExtendedCraftingRecipe {
    
    private static final Map<MatchStrategy, Function<? super ExtendedMatchRecipe, ? extends CraftingRecipe>> STRATEGY_MAPPER = new HashMap<>();
    static {
        registerMatchStrategy(VanillaMatchStrategy.SHAPED, recipe -> {
            ExtendedShapedRecipe shaped = (ExtendedShapedRecipe) recipe;
            NMSExtendedShapedRecipe nms = new NMSExtendedShapedRecipe(shaped);
            return new CRShaped(nms);
        });
        registerMatchStrategy(VanillaMatchStrategy.SHAPELESS, recipe -> {
            ExtendedShapelessRecipe shapeless = (ExtendedShapelessRecipe) recipe;
            NMSExtendedShapelessRecipe nms = new NMSExtendedShapelessRecipe(shapeless);
            return new CRShapeless(nms);
        });
    }
    
    public static void registerMatchStrategy(MatchStrategy strat, Function<? super ExtendedMatchRecipe, ? extends CraftingRecipe> bukkitMirror) {
        STRATEGY_MAPPER.put(strat, bukkitMirror);
    }
    
    private final MinecraftKey key;
    private final ItemStack result;
    private final NonNullList<? extends ExtendedCraftingIngredient> ingredients;
    private final MatchStrategy matcher;
    private final String group;

    private CraftingRecipe bukkit;
    
    public ExtendedMatchRecipe(MinecraftKey key, ItemStack result, NonNullList<? extends ExtendedCraftingIngredient> ingredients, MatchStrategy strat, String group) {
        this.key = key;
        this.result = result;
        this.ingredients = ingredients;
        this.matcher = strat;
        this.group = group;
    }

    @Override
    public ItemStack craftItem(InventoryCrafting inventory) {
        return result.cloneItemStack();
    }

    @Override
    public void setKey(MinecraftKey key) {
    }

    @Override
    public Recipe toBukkitRecipe() {
        if (matcher == VanillaMatchStrategy.SHAPED) {
            ExtendedShapedRecipe nms = (ExtendedShapedRecipe) this;
            ItemStack result = nms.getResult();
            Shape shape = nms.getShape();
            
            NamespacedKey bukkitKey = CraftNamespacedKey.fromMinecraft(nms.getKey());
            CraftItemStack bukkitResult = CraftItemStack.asCraftMirror(result);
            
            ShapedRecipe bukkit = new ShapedRecipe(bukkitKey, bukkitResult);
            bukkit.shape(shape.getPattern());
            
            shape.getIngredientMap().forEach((character, ingredient) -> ingredient.firstItemStack().ifPresent(itemStack ->
                    bukkit.setIngredient(character, CraftItemStack.asCraftMirror(itemStack).getData())));
            
            return bukkit;
        }
        
        //fallback on shapeless recipe, as CraftBukkit itself does this as well.
        ExtendedShapelessRecipe nms = (ExtendedShapelessRecipe) this;
        ItemStack result = nms.getResult();
        NonNullList<? extends ExtendedCraftingIngredient> ingredients = nms.getIngredients();
            
        NamespacedKey bukkitKey = CraftNamespacedKey.fromMinecraft(nms.getKey());
        CraftItemStack bukkitResult = CraftItemStack.asCraftMirror(result);
            
        ShapelessRecipe bukkit = new ShapelessRecipe(bukkitKey, bukkitResult);
        ingredients.forEach(ingredient -> ingredient.firstItemStack().ifPresent(itemStack ->
                bukkit.addIngredient(CraftItemStack.asCraftMirror(itemStack).getData())));
            
        return bukkit;
    }

    @Override
    public MinecraftKey getKey() {
        return key;
    }
    
    public MatchStrategy getMatcher() {
        return this.matcher;
    }
    
    private CraftingRecipe createBukkitRecipe() {
        Function<? super ExtendedMatchRecipe, ? extends CraftingRecipe> constructor = STRATEGY_MAPPER.get(getMatcher());
        if (constructor == null) {
            constructor = STRATEGY_MAPPER.get(VanillaMatchStrategy.SHAPELESS);
        }
        
        return constructor.apply(this);
    }

    @Override
    public CraftingRecipe getBukkitRecipe() {
        return bukkit == null ? bukkit = createBukkitRecipe() : bukkit;
    }

    @Override
    public boolean matches(InventoryCrafting inventory, World world) {
        return matcher.matches(this, inventory, world);
    }

    @Override
    public ItemStack getResult() {
        return result;
    }
    
    @Override
    public NonNullList<? extends ExtendedCraftingIngredient> getIngredients() {
        return ingredients;
    }

    @Override
    public NonNullList<ItemStack> getRemainders(InventoryCrafting inventory) {
        NonNullList<ItemStack> remainders = NonNullList.a(inventory.getSize(), ItemStack.a);
        ListIterator<ItemStack> remainderIterator = remainders.listIterator();
        
        List<ItemStack> matrix = inventory.getContents();
        NonNullList<? extends ExtendedCraftingIngredient> ingredients = getIngredients();
        
        Iterator<ItemStack> matrixIterator = matrix.iterator();
        Iterator<? extends ExtendedCraftingIngredient> ingredientIterator = ingredients.iterator();
        
        while (matrixIterator.hasNext() && ingredientIterator.hasNext() && remainderIterator.hasNext()) {
            ItemStack content = matrixIterator.next();
            ExtendedCraftingIngredient ingredient = ingredientIterator.next();
            
            ItemStack remainder = ingredient.getRemainder(content);

            remainderIterator.next();
            remainderIterator.set(remainder);
        }
        
        return remainders;
    }

    @Override
    public boolean isHidden() {
        return false;
    }
    
    @Override
    public String getGroup() {
        return group;
    }

}
