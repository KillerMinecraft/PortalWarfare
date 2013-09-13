package com.ftwinston.KillerMinecraft.Modules.DimensionalWarfare;

import java.util.Collections;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.ftwinston.KillerMinecraft.GameMode;
import com.ftwinston.KillerMinecraft.Helper;
import com.ftwinston.KillerMinecraft.Option;
import com.ftwinston.KillerMinecraft.WorldConfig;

public class DimensionalWarfare extends GameMode
{
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
	
	@Override
	public boolean isLocationProtected(Location l, Player player)
	{
		return false;
	}
	
	@Override
	public Environment[] getWorldsToGenerate() { return new Environment[] { Environment.NORMAL, Enivironment.NORMAL }; }
	
	@Override
	public void handlePortal(TeleportCause cause, Location entrance, PortalHelper helper)
	{
		if ( cause != TeleportCause.NETHER_PORTAL )
			return;
		
		World toWorld;
		double blockRatio;
		
		if ( entrance.getWorld() == getWorld(0) )
			toWorld = getWorld(1);
		else if ( entrance.getWorld() == getWorld(1) )
			toWorld = getWorld(0);
		}
		else
			return;
		
		helper.setupScaledDestination(toWorld, entrance, 1);
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event)
    {
		Block b = event.getBlock();
		if ( shouldIgnoreEvent(b) || b.getTypeId() != coreMaterial )
			return;
		
		for ( int team=0; team<2; team++ )
			if ( l.getBlock() == coreBlocks[team] )
			{
				if ( b.getType() != Material.EMERALD_BLOCK || coreStrengths[team] <= 0 )
					return;
				
				if ( --coreStrengths[team] < 1 )
				{
					finishGame();
					return;
				}				
			}
	}
	
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPortalCreated(org.bukkit.event.world.PortalCreateEvent event) throws EventException
	{
		if ( event.getReason() != CreateReason.OBC_DESTINATION )
			return;
		
		Block b = event.getBlocks()[0];
		
		int team = b.getWorld() == getWorld(0) ? 1 : 2;
		broadcastMessage(new PlayerFilter().team(team), "Warning! Portal created at " + b.getX() + ", " + b.getY() + ", " + b.getZ());
	}

	
	@Override
	public void gameStarted()
	{
		// create the "cores" and a basic team-colored buildingy thing around them
	}
}
