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
	Block[] coreBlocks = new Block[2];
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
				b.getRelative(BlockFace.NORTH).setType(Material.AIR);
				b.getRelative(BlockFace.SOUTH).setType(Material.AIR);
			}
			else
			{
				b.getRelative(BlockFace.EAST).setType(Material.AIR);
				b.getRelative(BlockFace.WEST).setType(Material.AIR);
			}
		}
			
		// then place the fire block that activates the portal
		if ( fireBlock != null )
			fireBlock.setType(Material.FIRE);
		
		creatingPortal = false;
	}
	
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEvent(BlockBreakEvent event)
	{
		Block b = event.getBlock();
		if ( b.getType() != Material.PORTAL )
			return;
		
		// portal was broken ... break the corresponding portal in the other world
		
		
		// ... and an obsidian block from its portal frame
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
		if ( b.getType() != coreMaterial )
			return;
		
		for ( int team=0; team<2; team++ )
			if ( b.getLocation() == coreBlocks[team].getLocation() )
			{
				if ( b.getType() != coreMaterial || coreStrengths[team] <= 0 )
					return;
				
				if ( --coreStrengths[team] < 1 )
				{
					finishGame();
					return;
				}				
			}
	}
	
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEvent(org.bukkit.event.entity.CreatureSpawnEvent event)
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
