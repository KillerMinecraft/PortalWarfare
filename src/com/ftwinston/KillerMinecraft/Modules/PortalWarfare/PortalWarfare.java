package com.ftwinston.KillerMinecraft.Modules.PortalWarfare;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.PortalCreateEvent.CreateReason;
import org.bukkit.inventory.ItemStack;

import com.ftwinston.KillerMinecraft.GameMode;
import com.ftwinston.KillerMinecraft.Helper;
import com.ftwinston.KillerMinecraft.Option;
import com.ftwinston.KillerMinecraft.PlayerFilter;
import com.ftwinston.KillerMinecraft.PortalHelper;
import com.ftwinston.KillerMinecraft.WorldConfig;

public class PortalWarfare extends GameMode
{
	private final Material coreMaterial = Material.EMERALD_BLOCK;
	public static final int allowDimensionalPicks = 0, reinforcedCores = 1;
	int coreBlockX, coreBlockY, coreBlockZ;
	int[] coreStrengths = new int[2];
	
	@Override
	public int getMinPlayers() { return 2; } // one player on each team is our minimum
	
	@Override
	public Option[] setupOptions()
	{
		Option[] options = {
			new Option("Allow 'Dimensional' pick axes", true),
			new Option("Reinforced cores", false),
		};
		
		return options;
	}
	
	@Override
	public String getHelpMessage(int num, int team)
	{
		switch ( num )
		{
			case 0:
			default:
				return null;
		}
	}
	
	long worldSeed;
	@Override
	public void beforeWorldGeneration(int worldNumber, WorldConfig world)
	{
		if ( worldNumber == 0 )
			worldSeed = world.getSeed();
		else
			world.setSeed(worldSeed);
	}
	
	@Override
	public boolean isLocationProtected(Location l, Player player)
	{
		return false;
	}
	
	@Override
	public Environment[] getWorldsToGenerate() { return new Environment[] { Environment.NORMAL, Environment.NORMAL }; }
	
	private World getOtherWorld(World world)
	{
		if ( world == getWorld(0) )
			return getWorld(1);
		
		return getWorld(0);
	}
	
	private Block getOtherWorldBlock(Block b)
	{
		World world = getOtherWorld(b.getWorld());
		return world.getBlockAt(b.getX(), b.getY(), b.getZ());
	}
	
	private int getTeamForWorld(World world)
	{
		return world == getWorld(0) ? 1 : 2;
	}
	
	boolean creatingPortal = false;
	
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPortalCreated(org.bukkit.event.world.PortalCreateEvent event)
	{
		if ( creatingPortal || event.getReason() == CreateReason.OBC_DESTINATION )
		{
			Block b = event.getBlocks().get(0);
			broadcastMessage(new PlayerFilter().team(getTeamForWorld(b.getWorld())), "Warning! Portal detected at " + b.getX() + ", " + b.getY() + ", " + b.getZ());
			return;
		}
		
		creatingPortal = true;
		
		// we want to create exit portals when the entrance is created, not when a portal is used
		// we also want to create them in EXACTLY the same place, which i can't seem to achieve using the built in stuff

		World toWorld = getOtherWorld(event.getWorld());
		
		// look through all the blocks returned by the event, decide if they're portal or frame, and also 
		// whether the portal faces north/south or east/west, for what data value to set
		
		boolean northSouth = false;
		Block fireBlock = null, test1 = null, test2 = null;
		ArrayList<Block> portalBlocks = new ArrayList<Block>(), obsidianBlocks = new ArrayList<Block>(); 
		for ( Block b : event.getBlocks() )
		{
			// corresponding block in other world should become portal
			Block dest = toWorld.getBlockAt(b.getLocation());
			if ( b.getType() == Material.OBSIDIAN )
				obsidianBlocks.add(dest);
			else // air or fire
			{
				if ( b.getType() == Material.FIRE )
					fireBlock = dest;
				
				portalBlocks.add(dest);
				if ( test1 == null )
					test1 = dest;
				else if ( test2 == null )
				{
					if ( dest.getX() != test1.getX() )
						northSouth = true;
					else if ( dest.getZ() != test1.getZ() )
						northSouth = false;
					else
						continue;
					
					test2 = dest;
				}
			}
		}
		
		// place the frame
		for ( Block b : obsidianBlocks )
			b.setType(Material.OBSIDIAN);
						
		// place air blocks inside, as well as in front of & behind the frame
		for ( Block b : portalBlocks )
		{	
			b.setType(Material.PORTAL);
			
			if ( northSouth )
			{
				setBlockIfNotCore(b.getRelative(BlockFace.NORTH), Material.AIR);
				setBlockIfNotCore(b.getRelative(BlockFace.SOUTH), Material.AIR);
			}
			else
			{
				setBlockIfNotCore(b.getRelative(BlockFace.EAST), Material.AIR);
				setBlockIfNotCore(b.getRelative(BlockFace.WEST), Material.AIR);
			}
		}
			
		// then place the fire block that activates the portal
		if ( fireBlock != null )
			fireBlock.setType(Material.FIRE);
		
		creatingPortal = false;
	}
	
	private void setBlockIfNotCore(Block b, Material type)
	{
		if ( b.getType() != coreMaterial || b.getX() != coreBlockX || b.getZ() != coreBlockZ || b.getY() != coreBlockY )
			b.setType(type);
	}
	
	@Override
	public void handlePortal(TeleportCause cause, Location entrance, PortalHelper helper)
	{
		if ( cause != TeleportCause.NETHER_PORTAL )
			return;
		
		World toWorld = getOtherWorld(entrance.getWorld());
		
		helper.setExitPortalCreationRadius(0);
		helper.setExitPortalSearchRadius(8);
		helper.setDestination(new Location(toWorld, entrance.getX(), entrance.getY(), entrance.getZ(), entrance.getYaw(), entrance.getPitch()));
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event)
    {
		Block b = event.getBlock();
		if ( b.getType() == coreMaterial )
		{		
			if ( b.getX() == coreBlockX && b.getZ() == coreBlockZ && b.getY() == coreBlockY )
			{
				int team = getTeamForWorld(event.getBlock().getWorld()) - 1;
				if ( b.getType() != coreMaterial || coreStrengths[team] <= 0 )
					return;
				
				if ( --coreStrengths[team] < 1 )
					finishGame();
				
				return;
			}
		}
		
		
		if ( b.getType() == Material.PORTAL )
		{
			// portal was broken (by creative mode) ... break the corresponding portal in the other world
			getOtherWorldBlock(event.getBlock()).setType(Material.AIR);
			return;
		}
		else if ( b.getType() == Material.OBSIDIAN ) 
		{
			// if this was part of a portal frame, break the corresponding block in other world
			if ( b.getRelative(BlockFace.NORTH).getType() == Material.PORTAL
			  || b.getRelative(BlockFace.SOUTH).getType() == Material.PORTAL
			  || b.getRelative(BlockFace.EAST).getType() == Material.PORTAL
			  || b.getRelative(BlockFace.WEST).getType() == Material.PORTAL )
			{
				getOtherWorldBlock(event.getBlock()).setType(Material.AIR);
				return;
			}
		}
		
		// if broken with a dimensional pick, break the corresponding block in other world
		ItemStack item = event.getPlayer().getItemInHand(); 
		if ( item != null && item.getType() == Plugin.dimensionalPickMaterial && item.getEnchantmentLevel(Plugin.dimensionalPickEnchant) > 0 )
		{
			getOtherWorldBlock(event.getBlock()).setType(Material.AIR);
			item.setDurability((short)Math.max(item.getDurability() - Plugin.dimensionalPickDurabilityLoss, 0));
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onCreatureSpawn(org.bukkit.event.entity.CreatureSpawnEvent event)
	{
		if ( event.getEntityType() == EntityType.PIG_ZOMBIE && event.getSpawnReason() == SpawnReason.SPAWNER_EGG )
			event.setCancelled(true);
	}
	
	@Override
	protected void gameStarted() {
		
	}

	@Override
	protected void gameFinished() {
	}


	@Override
	public boolean teamAllocationIsSecret() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isAllowedToRespawn(Player player) { return true; }

	@Override
	public Location getSpawnLocation(Player player)
	{
		int team = Helper.getTeam(getGame(), player);
		Location spawnPoint = Helper.randomizeLocation(getWorld(team <= 0 ? 0 : team-1).getSpawnLocation(), 0, 0, 0, 8, 0, 8);
		return Helper.getSafeSpawnLocationNear(spawnPoint);
	}
	
	@Override
	public String describe() {
		return "Dimensional Warfare\nTwo teams, each in their own worlds, connected by portals. Players must defend their own \"core\" block, while trying to destroy the enemy's.";
	}
}
