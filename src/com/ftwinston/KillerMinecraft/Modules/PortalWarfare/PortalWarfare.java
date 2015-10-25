package com.ftwinston.KillerMinecraft.Modules.PortalWarfare;

import java.util.ArrayList;

import org.bukkit.ChatColor;
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
	
	TeamInfo redTeam = new TeamInfo() {
		@Override
		public String getName() { return "red team"; }
		@Override
		public ChatColor getChatColor() { return ChatColor.RED; }
		@Override
		public byte getWoolColor() { return (byte)0xE; }
	};
	TeamInfo blueTeam = new TeamInfo() {
		@Override
		public String getName() { return "blue team"; }
		@Override
		public ChatColor getChatColor() { return ChatColor.BLUE; }
		@Override
		public byte getWoolColor() { return (byte)0xB; }
	};
	
	TeamInfo[] teams = new TeamInfo[] { redTeam, blueTeam };
	
	public PortalWarfare()
	{
		setTeams(teams);
	}
	
	@Override
	public Option[] setupOptions()
	{
		allowDimensionalPicks = new ToggleOption("Allow 'Dimensional' pickaxes", true, "A pickaxe that breaks blocks", "in both worlds simultaneously.", "To build, craft an obsidian pickaxe.");
		
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
				return "Near each spawn is a small team-colored fort, with an emerald \"core block\" block on top.";
			case 2:
				return "Destroying the other team's core block decreases their score. When it reaches zero, the other team wins.";
			case 3:
				return "Move between the two worlds by building a nether portal. Portals emerge in the exact same location in the other world.";
			case 4:
				return "Portals are one-way, and a warning message will be shown when a portal is created, telling everyone the location.";
			case 5:
				return "You can destroy a portal by breaking its \"exit\" frame. There is no 'visible' portal inside an exit frame.";
			case 6:
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
		
		String name = redTeam.getChatColor() + redTeam.getName();
		Score score = objective.getScore(name);
		score.setScore(25);
		redTeam.setScoreboardScore(score);
		
		name = blueTeam.getChatColor() + blueTeam.getName();
		score = objective.getScore(name);
		score.setScore(25);
		blueTeam.setScoreboardScore(score);
		
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
	
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPortalCreated(org.bukkit.event.world.PortalCreateEvent event)
	{
		if (event.getReason() == CreateReason.OBC_DESTINATION)
		{
			return;
		}
		
		World fromWorld = event.getWorld();
		World toWorld = getOtherWorld(fromWorld);
		TeamInfo fromTeam = getTeamForWorld(fromWorld);
		TeamInfo toTeam = getTeamForWorld(toWorld);

		Block bReport = event.getBlocks().get(0);
		broadcastMessage("Warning! Portal created from " + fromTeam.getChatColor() + fromTeam.getName() + ChatColor.RESET + "'s world to " + toTeam.getChatColor() + toTeam.getName() + ChatColor.RESET + " at " + bReport.getX() + ", " + bReport.getY() + ", " + bReport.getZ());
		
		// we want to create "exit portals" when the entrance is created, not when a portal is used
		// we also want to create them in EXACTLY the same place, which i can't seem to achieve using the built in stuff

		// look through all the blocks returned by the event, decide if they're portal or frame, and also 
		// whether the portal faces north/south or east/west, for what data value to set
		
		boolean northSouth = false;
		Block test1 = null, test2 = null;
		ArrayList<Block> airBlocks = new ArrayList<Block>(), frameBlocks = new ArrayList<Block>();
		for ( Block b : event.getBlocks() )
		{
			Block dest = toWorld.getBlockAt(b.getLocation());
			if ( b.getType() == Material.OBSIDIAN )
				frameBlocks.add(dest);
			else // air or fire
			{
				airBlocks.add(dest);
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
		
		// place air blocks inside, as well as in front of & behind the frame
		for ( Block b : airBlocks )
		{	
			b.setType(Material.AIR);
			
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
		
		// corresponding block in other world should become "exit portal"
		Location minBound = frameBlocks.get(0).getLocation();
		Location maxBound = frameBlocks.get(0).getLocation();
		for (Block b : frameBlocks)
		{
			b.setType(Material.ENDER_STONE);
			
			if (b.getX() < minBound.getBlockX())
				minBound.setX(b.getX());
			if (b.getX() > maxBound.getBlockX())
				maxBound.setX(b.getX());

			if (b.getY() < minBound.getBlockY())
				minBound.setY(b.getY());
			if (b.getY() > maxBound.getBlockY())
				maxBound.setY(b.getY());

			if (b.getZ() < minBound.getBlockZ())
				minBound.setZ(b.getZ());
			if (b.getZ() > maxBound.getBlockZ())
				maxBound.setZ(b.getZ());
		}
		
		// the event no longer seems to include the "bottom row" of the portal frame blocks, so we need to add those
		int y = minBound.getBlockY() - 1;
		if (northSouth)
		{
			int z = minBound.getBlockZ();
			for (int x = minBound.getBlockX() + 1; x < maxBound.getBlockX(); x++)
				toWorld.getBlockAt(x, y, z).setType(Material.ENDER_STONE);
		}
		else
		{
			int x = minBound.getBlockX();
			for (int z = minBound.getBlockZ() + 1; z < maxBound.getBlockZ(); z++)
				toWorld.getBlockAt(x, y, z).setType(Material.ENDER_STONE);
		}
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
				TeamInfo team = getTeamForWorld(event.getBlock().getWorld());
				if ( b.getType() != coreMaterial || team.getScoreboardScore().getScore() <= 0 )
					return;
				
				int score = team.getScoreboardScore().getScore() - 1;
				team.getScoreboardScore().setScore(score);
				if ( score < 1 )
					finishGame();
				else
					event.setCancelled(true);
			}
			
			return;
		}
		
		if (b.getType() == Material.ENDER_STONE)
		{
			// portal "exit frame" was broken. break the corresponding block in the other world
			Block otherBlock = getOtherWorldBlock(event.getBlock());
			if (otherBlock.getType() == Material.OBSIDIAN)
				otherBlock.setType(Material.AIR);
			
			// prevent from dropping actual block
			b.setType(Material.AIR);
			event.setCancelled(true);
			return;
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
	
	@SuppressWarnings("deprecation")
	@Override
	protected void gameStarted() {
		// create "cores" ... first, pick a location near to the spawn
		Location loc = getWorld(0).getSpawnLocation();
		loc = Helper.randomizeLocation(loc, 30, 0, 30, 40, 0, 40);
		loc.setY(getWorld(0).getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ()) + 2);
		
		coreBlockX = loc.getBlockX(); coreBlockY = loc.getBlockY(); coreBlockZ = loc.getBlockZ();
		
		broadcastMessage("The core blocks are at " + coreBlockX + ", " + coreBlockY + ", " + coreBlockZ + " in each world.");
		
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
				while ( y > 0 && (b.isEmpty() || b.isLiquid() || b.getType() == Material.GRASS || b.getType() == Material.LONG_GRASS || b.getType() == Material.LEAVES) )
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
		if ( redTeam.getScoreboardScore().getScore() <= 0 )
			broadcastMessage("The " + blueTeam.getChatColor() + "blue team " + ChatColor.RESET + " destroyed the red core, and win the game!");
		else if ( blueTeam.getScoreboardScore().getScore() <= 0 )
			broadcastMessage("The " + redTeam.getChatColor() + "red team " + ChatColor.RESET + "destroyed the blue core, and win the game!");
		else
			broadcastMessage("Game drawn.");
	}

	@Override
	public Location getSpawnLocation(Player player)
	{
		World world = getWorld(getTeam(player) == redTeam ? 0 : 1);
		Location spawnPoint = Helper.randomizeLocation(world.getSpawnLocation(), 0, 0, 0, 8, 0, 8);
		return Helper.getSafeSpawnLocationNear(spawnPoint);
	}
}
