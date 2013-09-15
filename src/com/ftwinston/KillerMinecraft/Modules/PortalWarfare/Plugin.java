package com.ftwinston.KillerMinecraft.Modules.PortalWarfare;

import java.util.ArrayList;
import java.util.Arrays;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import com.ftwinston.KillerMinecraft.GameMode;
import com.ftwinston.KillerMinecraft.GameModePlugin;

public class Plugin extends GameModePlugin
{
	@Override
	public GameMode createInstance()
	{
		return new PortalWarfare();
	}
	
	static final Enchantment dimensionalPickEnchant = Enchantment.ARROW_FIRE;
	static final Material dimensionalPickMaterial = Material.IRON_PICKAXE;
	static final short dimensionalPickDurabilityLoss = 10; // extra durability loss when dimensionally picking
	
	@Override
	protected ArrayList<Recipe> createCustomRecipes()
	{
		ArrayList<Recipe> recipes = new ArrayList<Recipe>();
		ShapedRecipe shaped;
		
		ItemStack dimensionalPick = new ItemStack(dimensionalPickMaterial);
		dimensionalPick.addUnsafeEnchantment(dimensionalPickEnchant, 1);
		ItemMeta meta = dimensionalPick.getItemMeta();
		meta.setDisplayName("Dimensional Pick");
		meta.setLore(Arrays.asList("Breaks blocks in the other world", "as well as in your own"));
		dimensionalPick.setItemMeta(meta);
		
		shaped = new ShapedRecipe(dimensionalPick);
		shaped.shape(new String[] { "AAA", " B ", " B " });
		shaped.setIngredient('A', Material.OBSIDIAN);
		shaped.setIngredient('B', Material.STICK);
		recipes.add(shaped);
		
		return recipes;
	}		
}