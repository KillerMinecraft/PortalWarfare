package com.ftwinston.KillerMinecraft.Modules.DimensionalWarfare;

import com.ftwinston.KillerMinecraft.GameMode;
import com.ftwinston.KillerMinecraft.GameModePlugin;
import com.ftwinston.KillerMinecraft.KillerMinecraft;

public class Plugin extends GameModePlugin
{
	public void onEnable()
	{
		KillerMinecraft.registerGameMode(this);
	}
	
	@Override
	public GameMode createInstance()
	{
		return new DimensionalWarfare();
	}
}