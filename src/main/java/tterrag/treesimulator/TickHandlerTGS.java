package tterrag.treesimulator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSapling;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.BonemealEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;

public class TickHandlerTGS
{
	private Map<String, Integer> counters = new HashMap<String, Integer>();
	private Map<String, PlayerState> states = new HashMap<String, PlayerState>();
	private int ticksSinceLastCheck = 0;

	public enum PlayerState
	{
		CROUCHED, STANDING;

		public static PlayerState getState(boolean bool)
		{
			return bool ? CROUCHED : STANDING;
		};
	};

	private class Coord
	{
		public int x, y, z;

		public Coord(int x, int y, int z)
		{
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}


	@SubscribeEvent
	public void onPlayerTick(PlayerTickEvent event)
	{
	    Integer temp = counters.get(event.player.getCommandSenderName());
	    int movementCounter = temp == null ? 0 : temp;
	    
		if (event.phase == Phase.END && !event.player.worldObj.isRemote)
		{
			EntityPlayer player = (EntityPlayer) event.player;
			if (ticksSinceLastCheck >= 5)
			{
				List<Coord> coords = getNearestBlocks(player.worldObj, (int) player.posX, (int) player.posY, (int) player.posZ);

				if (coords.size() == 0)
					return;

				if (player.isSprinting() && TreeSimulator.enableSprint)
				{
					movementCounter++;
					doEngines(coords, player.worldObj);
				}
				if (PlayerState.getState(player.isSneaking()) != getState(player))
				{
					movementCounter++;
					doEngines(coords, player.worldObj);
				}
				if (movementCounter > TreeSimulator.waitTime)
				{
					if (coords.size() == 0)
					{
						movementCounter--;
						return;
					}
					
					Collections.shuffle(coords);

					for (Coord pos : coords)
					{
						Block block = player.worldObj.getBlock(pos.x, pos.y, pos.z);

						if (block instanceof BlockSapling)
						{
							BonemealEvent bonemeal = new BonemealEvent(player, player.worldObj, player.worldObj.getBlock(pos.x, pos.y, pos.z), pos.x, pos.y, pos.z);
							MinecraftForge.EVENT_BUS.post(bonemeal);

							BlockSapling sapling = (BlockSapling) block;
							if ((double) player.worldObj.rand.nextFloat() < 0.45D)
								sapling.func_149879_c(player.worldObj, pos.x, pos.y, pos.z, player.worldObj.rand);

							if (TreeSimulator.showParticles && sapling == Blocks.sapling)
								sendPacket(pos.x, pos.y, pos.z);
							
							break;
						}
					}
					movementCounter = 0;
				}
			}
			else
			{
				ticksSinceLastCheck++;
			}

			states.put(player.getCommandSenderName(), PlayerState.getState(player.isSneaking()));
			counters.put(event.player.getCommandSenderName(), movementCounter);
		}
	}
	
	private PlayerState getState(EntityPlayer player) 
	{
	    String user = player.getCommandSenderName();
	    if (!states.containsKey(user)) {
	        states.put(user, PlayerState.getState(player.isSneaking()));
	    }
	    return states.get(user);
	}

	private void doEngines(List<Coord> coords, World world)
	{
		for (Coord pos : coords)
		{
			Block block = world.getBlock(pos.x, pos.y, pos.z);

			if (block instanceof BlockEngine)
			{
				TileEngine te = (TileEngine) world.getTileEntity(pos.x, pos.y, pos.z);
				if (te != null)
				{
					te.bumpEnergy(world.isRemote ? 0 : TreeSimulator.energyPerBump);
				}
			}
		}
	}

	private void sendPacket(int x, int y, int z)
	{
		PacketHandlerTGS.INSTANCE.sendToAll(new MessageBonemealParticles(x, y, z));
	}

	private List<Coord> getNearestBlocks(World world, int xpos, int ypos, int zpos)
	{
		List<Coord> list = new ArrayList<Coord>();
		for (int x = -5; x <= 5; x++)
			for (int y = -2; y <= 2; y++)
				for (int z = -5; z <= 5; z++)
				{
					Block block = world.getBlock(x + xpos, y + ypos, z + zpos);
					if (block instanceof BlockSapling || block instanceof BlockEngine)
						list.add(new Coord(x + xpos, y + ypos, z + zpos));
				}
		return list;
	}
}
