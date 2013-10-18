package com.ftwinston.KillerMinecraft.Modules.PortalWarfare;

import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import com.ftwinston.KillerMinecraft.GameMode;
import com.ftwinston.KillerMinecraft.Helper;
import com.ftwinston.KillerMinecraft.Option;
import com.ftwinston.KillerMinecraft.PortalHelper;
import com.ftwinston.KillerMinecraft.WorldConfig;
import com.ftwinston.KillerMinecraft.Configuration.TeamInfo;
import com.ftwinston.KillerMinecraft.Configuration.ToggleOption;

public class PortalWarfare extends GameMode
{
	private final Material coreMaterial = Material.EMERALD_BLOCK, fortMaterial = Material.WOOL;
	private ToggleOption allowDimensionalPicks;
	int coreBlockX, coreBlockY, coreBlockZ;
	
	@Override
	public int getMinPlayers() { return 2; } // one player on each team is our minimum
	
	abstract class PortalTeamInfo extends TeamInfo
	{
		public Score score;
	}
	
	PortalTeamInfo redTeam = new PortalTeamInfo() {
		@Override
		public String getName() { return "red team"; }
		@Override
		public ChatColor getChatColor() { return ChatColor.RED; }
		@Override
		public byte getWoolColor() { return (byte)0xE; }
	};
	PortalTeamInfo blueTeam = new PortalTeamInfo() {
		@Override
		public String getName() { return "blue team"; }
		@Override
		public ChatColor getChatColor() { return ChatColor.BLUE; }
		@Override
		public byte getWoolColor() { return (byte)0xB; }
	};
	
	PortalTeamInfo[] teams = new PortalTeamInfo[] { redTeam, blueTeam };
	
	public PortalWarfare()
	{
		setTeams(teams);
	}
	
	@Override
	public Option[] setupOptions()
	{
		allowDimensionalPicks = new ToggleOption("Allow 'Dimensional' pick axes", true, "A pickaxe that breaks blocks", "in both worlds simultaneously.", "To build, craft an obsidian pickaxe.");
		
		Option[] options = { allowDimensionalPicks };
		
		return options;
	}
	
	@Override
	public String getHelpMessage(int num, TeamInfo team)
	{
		switch ( num )
		{
			case 0:
				return "Each team spawns in their own world. The worlds are identical.";
			case 1:
				return "Near each spawn is a small team-colored fort, with an emerald block on top.";
			case 2:
				return "Destroying the other team's emerald decreases their score. When it reaches zero, the other team wins.";
			case 3:
				return "Move between the two worlds by building a nether portal. Portals emerge in the exact same location in the other world.";
			case 4:
				return "Players will receive a warning message when a portal is created, telling them the location.";
			case 5:
				return allowDimensionalPicks.isEnabled() ? "Craft a 'dimensional' pick using obsidian. This breaks blocks in both worlds when used!" : null;
			default:
				return null;
		}
	}
	
	Objective objective;
	
	@Override
	public Scoreboard createScoreboard()
	{
		Scoreboard scoreboard = super.createScoreboard();
		
		objective = scoreboard.registerNewObjective("cores", "dummy");
		objective.setDisplaySlot(DisplaySlot.SIDEBAR);
		objective.setDisplayName("Core strength");
		
		OfflinePlayer fake = Bukkit.getServer().getOfflinePlayer(redTeam.getChatColor() + redTeam.getName());
		Score score = objective.getScore(fake);
		score.setScore(25);
		redTeam.score = score;
		
		fake = Bukkit.getServer().getOfflinePlayer(blueTeam.getChatColor() + blueTeam.getName());
		score = objective.getScore(fake);
		score.setScore(25);
		blueTeam.score = score;
		
		return scoreboard;
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
	
	private TeamInfo getTeamForWorld(World world)
	{		
		return world == getWorld(0) ? redTeam : blueTeam;
	}
	
	boolean creatingPortal = false;
	
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPortalCreated(org.bukkit.event.world.PortalCreateEvent event)
	{
		if ( creatingPortal || event.getReason() == CreateReason.OBC_DESTINATION )
		{
			Block b = event.getBlocks().get(0);
			broadcastMessage("Warning! Portal created at " + b.getX() + ", " + b.getY() + ", " + b.getZ());
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
	public void handlePortal(TeleportCause cause, Location entityLoc, PortalHelper helper)
	{
		if ( cause != TeleportCause.NETHER_PORTAL )
			return;

		// however you move INTO a portal, you should emerge at exactly the same location, in the other world.
		World toWorld = getOtherWorld(entityLoc.getWorld());
		helper.setUseExitPortal(false);
		helper.setDestination(new Location(toWorld, entityLoc.getX(), entityLoc.getY(), entityLoc.getZ(), entityLoc.getYaw(), entityLoc.getPitch()));
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event)
    {
		Block b = event.getBlock();
		if ( b.getType() == coreMaterial )
		{		
			if ( b.getX() == coreBlockX && b.getZ() == coreBlockZ && b.getY() == coreBlockY )
			{
				PortalTeamInfo team = (PortalTeamInfo)getTeamForWorld(event.getBlock().getWorld());
				if ( b.getType() != coreMaterial || team.score.getScore() <= 0 )
					return;
				
				int score = team.score.getScore() - 1;
				team.score.setScore(score);
				if ( score < 1 )
					finishGame();
				else
					event.setCancelled(true);
				
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
			  || b.getRelative(BlockFace.WEST).getType() == Material.PORTAL
			  || b.getRelative(BlockFace.UP).getType() == Material.PORTAL
			  || b.getRelative(BlockFace.DOWN).getType() == Material.PORTAL )
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
		// create "cores" ... first, pick a location near to the spawn
		Location loc = getWorld(0).getSpawnLocation();
		loc = Helper.randomizeLocation(loc, 30, 0, 30, 40, 0, 40);
		loc.setY(getWorld(0).getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ()) + 2);
		
		coreBlockX = loc.getBlockX(); coreBlockY = loc.getBlockY(); coreBlockZ = loc.getBlockZ();
		
		// create the core block and surround it with a wee bit of a fort
		World[] worlds = new World[] { getWorld(0), getWorld(1) };
		
		for ( int i=0; i<2; i++ )
		{
			World world = worlds[i];
			TeamInfo team = teams[i];
			
			for ( int x = coreBlockX - 2; x <= coreBlockX + 2; x ++)
				for ( int z = coreBlockZ - 2; z <= coreBlockZ + 2; z ++)
					for ( int y = coreBlockY - 2; y <= coreBlockY + 2; y ++)
					{
						Block b = world.getBlockAt(x, y, z);
						b.setType(Material.AIR);
					}
			
			world.getBlockAt(coreBlockX, coreBlockY, coreBlockZ).setType(coreMaterial);
			
			for ( int x = coreBlockX - 3; x <= coreBlockX + 3; x ++)
				for ( int z = coreBlockZ - 3; z <= coreBlockZ + 3; z ++)
					for ( int y = coreBlockY - 5; y <= coreBlockY - 3; y ++)
					{
						Block b = world.getBlockAt(x, y, z);
						b.setType(fortMaterial);
						b.setData(team.getWoolColor());
					}
			
			for ( int x = coreBlockX - 3; x <= coreBlockX + 3; x ++)
			{
				Block b = world.getBlockAt(x, coreBlockY-2, coreBlockZ - 3);
				b.setType(fortMaterial);
				b.setData(team.getWoolColor());
				
				b = world.getBlockAt(x, coreBlockY-2, coreBlockZ + 3);
				b.setType(fortMaterial);
				b.setData(team.getWoolColor());
			}
			
			for ( int z = coreBlockZ - 3; z <= coreBlockZ + 3; z ++)
			{
				Block b = world.getBlockAt(coreBlockX - 3, coreBlockY-2, z);
				b.setType(fortMaterial);
				b.setData(team.getWoolColor());
				
				b = world.getBlockAt(coreBlockX + 3, coreBlockY-2, z);
				b.setType(fortMaterial);
				b.setData(team.getWoolColor());
			}
			
			int[] xs = new int[] { coreBlockX - 3, coreBlockX + 3, coreBlockX - 3, coreBlockX + 3 }, zs = new int[] { coreBlockZ + 3, coreBlockZ + 3, coreBlockZ - 3, coreBlockZ - 3 }; 
			
			for ( int j=0; j<4; j++ )
			{
				int y = coreBlockY - 6;
				Block b = world.getBlockAt(xs[j], y, zs[j]);
				while ( y > 0 && (b.isEmpty() || b.isLiquid()) )
				{
					b.setType(fortMaterial);
					b.setData(team.getWoolColor());
					y--;
					b = world.getBlockAt(xs[j], y, zs[j]);
				}
			}
		}
	}

	@Override
	protected void gameFinished()
	{
		if ( redTeam.score.getScore() <= 0 )
			broadcastMessage("The " + blueTeam.getChatColor() + "blue team " + ChatColor.RESET + " destroyed the red core, and win the game!");
		else if ( blueTeam.score.getScore() <= 0 )
			broadcastMessage("The " + redTeam.getChatColor() + "red team " + ChatColor.RESET + "destroyed the blue core, and win the game!");
		else
			broadcastMessage("Game drawn.");
	}

	@Override
	public boolean isAllowedToRespawn(Player player) { return true; }

	@Override
	public Location getSpawnLocation(Player player)
	{
		World world = getWorld(getTeam(player) == redTeam ? 0 : 1);
		Location spawnPoint = Helper.randomizeLocation(world.getSpawnLocation(), 0, 0, 0, 8, 0, 8);
		return Helper.getSafeSpawnLocationNear(spawnPoint);
	}
}
