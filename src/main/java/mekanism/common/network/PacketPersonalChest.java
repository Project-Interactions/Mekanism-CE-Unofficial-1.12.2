package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.common.Mekanism;
import mekanism.common.PacketHandler;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.inventory.InventoryPersonalChest;
import mekanism.common.network.PacketPersonalChest.PersonalChestMessage;
import mekanism.common.tile.TileEntityPersonalChest;
import mekanism.common.util.MekanismUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketPersonalChest implements IMessageHandler<PersonalChestMessage, IMessage>
{
	@Override
	public IMessage onMessage(PersonalChestMessage message, MessageContext context) 
	{
		EntityPlayer player = PacketHandler.getPlayer(context);
		
		if(message.packetType == PersonalChestPacketType.SERVER_OPEN)
		{
			try {
				if(message.isBlock)
				{
					TileEntityPersonalChest tileEntity = (TileEntityPersonalChest)message.coord4D.getTileEntity(player.worldObj);
					MekanismUtils.openPersonalChestGui((EntityPlayerMP)player, tileEntity, null, true);
				}
				else {
					ItemStack stack = player.getCurrentEquippedItem();

					if(MachineType.get(stack) == MachineType.PERSONAL_CHEST)
					{
						InventoryPersonalChest inventory = new InventoryPersonalChest(player);
						MekanismUtils.openPersonalChestGui((EntityPlayerMP)player, null, inventory, false);
					}
				}
			} catch(Exception e) {
				Mekanism.logger.error("Error while handling electric chest open packet.");
				e.printStackTrace();
			}
		}
		else if(message.packetType == PersonalChestPacketType.CLIENT_OPEN)
		{
			try {
				Mekanism.proxy.openPersonalChest(player, message.guiType, message.windowId, message.isBlock, message.coord4D == null ? BlockPos.ORIGIN : message.coord4D.getPos());
			} catch(Exception e) {
				Mekanism.logger.error("Error while handling electric chest open packet.");
				e.printStackTrace();
			}
		}
		
		return null;
	}
	
	public static class PersonalChestMessage implements IMessage
	{
		public PersonalChestPacketType packetType;
	
		public boolean isBlock;
	
		public int guiType;
		public int windowId;
	
		public Coord4D coord4D;
		
		public PersonalChestMessage() {}
	
		//This is a really messy implementation...
		public PersonalChestMessage(PersonalChestPacketType type, boolean b1, int i1, int i2, Coord4D c1)
		{
			packetType = type;
	
			switch(packetType)
			{
				case CLIENT_OPEN:
					guiType = i1;
					windowId = i2;
					isBlock = b1;
	
					if(isBlock)
					{
						coord4D = c1;
					}
	
					break;
				case SERVER_OPEN:
					isBlock = b1;
	
					if(isBlock)
					{
						coord4D = c1;
					}
	
					break;
			}
		}
	
		@Override
		public void toBytes(ByteBuf dataStream)
		{
			dataStream.writeInt(packetType.ordinal());
	
			switch(packetType)
			{
				case CLIENT_OPEN:
					dataStream.writeInt(guiType);
					dataStream.writeInt(windowId);
					dataStream.writeBoolean(isBlock);
	
					if(isBlock)
					{
						coord4D.write(dataStream);
					}
	
					break;
				case SERVER_OPEN:
					dataStream.writeBoolean(isBlock);
	
					if(isBlock)
					{
						coord4D.write(dataStream);
					}
	
					break;
			}
		}
	
		@Override
		public void fromBytes(ByteBuf dataStream)
		{
			packetType = PersonalChestPacketType.values()[dataStream.readInt()];
	
			if(packetType == PersonalChestPacketType.SERVER_OPEN)
			{
				isBlock = dataStream.readBoolean();

				if(isBlock)
				{
					coord4D = new Coord4D(dataStream.readInt(), dataStream.readInt(), dataStream.readInt(), dataStream.readInt());
				}
			}
			else if(packetType == PersonalChestPacketType.CLIENT_OPEN)
			{
				guiType = dataStream.readInt();
				windowId = dataStream.readInt();
				isBlock = dataStream.readBoolean();

				if(isBlock)
				{
					coord4D = new Coord4D(dataStream.readInt(), dataStream.readInt(), dataStream.readInt(), dataStream.readInt());
				}
			}
		}
	}
	
	public static enum PersonalChestPacketType
	{
		CLIENT_OPEN,
		SERVER_OPEN
	}
}
