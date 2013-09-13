package com.ftwinston.KillerMinecraft.Modules.DimensionalWarfare;

import com.ftwinston.KillerMinecraft.GameMode;
import com.ftwinston.KillerMinecraft.GameModePlugin;

public class Plugin extends GameModePlugin
{
	@Override
	public GameMode createInstance()
	{
		return new DimensionalWarfare();
	}
}