package com.ftwinston.KillerMinecraft.Modules.DimensionalWarfare;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.PortalCreateEvent.CreateReason;

import com.ftwinston.KillerMinecraft.GameMode;
import com.ftwinston.KillerMinecraft.Helper;
import com.ftwinston.KillerMinecraft.Option;
import com.ftwinston.KillerMinecraft.PlayerFilter;
import com.ftwinston.KillerMinecraft.PortalHelper;
import com.ftwinston.KillerMinecraft.WorldConfig;

public class DimensionalWarfare extends GameMode
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
	
	@Override
	public void handlePortal(TeleportCause cause, Location entrance, PortalHelper helper)
	{
		if ( cause != TeleportCause.NETHER_PORTAL )
			return;
		
		World toWorld;
		
		if ( entrance.getWorld() == getWorld(0) )
			toWorld = getWorld(1);
		else if ( entrance.getWorld() == getWorld(1) )
			toWorld = getWorld(0);
		else
			return;
		
		helper.setupScaledDestination(toWorld, entrance, 1);
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
	public void onPortalCreated(org.bukkit.event.world.PortalCreateEvent event)
	{
		if ( event.getReason() != CreateReason.OBC_DESTINATION )
			return;
		
		Block b = event.getBlocks().get(0);
		
		int team = b.getWorld() == getWorld(0) ? 1 : 2;
		broadcastMessage(new PlayerFilter().team(team), "Warning! Portal created at " + b.getX() + ", " + b.getY() + ", " + b.getZ());
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
		return "Two teams, each in their own worlds, connected by portals. Players must defend their own \"core\" block, while trying to destroy the enemy's.";
	}
}
