package cf.wayzer.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

public class WorldBorderUtil {
	private static Class<?> class_worldBorder;
	private static Class<?> class_packet;
	private static Class<?> class_enum;
	private static Constructor<?> constructor;
	private static Object handle;
	private static WorldBorder worldBorder;

	private static Method method_sendPacket;
	private static Field field_playerConnection;
	private static Method method_getHandle;

	static {
		try {
			Class<?> class_craftworldBorder = Class
					.forName("org.bukkit.craftbukkit." + getVersion() + ".CraftWorldBorder");
			Class<?> class_PlayerConnection = Class
					.forName("net.minecraft.server." + getVersion() + ".PlayerConnection");
			Class<?> class_Packet = Class.forName("net.minecraft.server." + getVersion() + ".Packet");
			Class<?> class_CraftPlayer = Class
					.forName("org.bukkit.craftbukkit." + getVersion() + ".entity.CraftPlayer");
			Class<?> class_EntityPlayer = Class.forName("net.minecraft.server." + getVersion() + ".EntityPlayer");
			class_worldBorder = Class.forName("net.minecraft.server." + getVersion() + ".WorldBorder");
			class_packet = Class.forName("net.minecraft.server." + getVersion() + ".PacketPlayOutWorldBorder");
			class_enum = Class.forName(
					"net.minecraft.server." + getVersion() + ".PacketPlayOutWorldBorder$EnumWorldBorderAction");

			method_sendPacket = class_PlayerConnection.getMethod("sendPacket", class_Packet);
			method_getHandle = class_CraftPlayer.getMethod("getHandle");
			constructor = class_packet.getConstructor(class_worldBorder, class_enum);
			Constructor<?> constructor1 = class_craftworldBorder.getConstructors()[0];

			Field field = class_craftworldBorder.getDeclaredField("handle");
			field_playerConnection = class_EntityPlayer.getField("playerConnection");

			constructor1.setAccessible(true);
			field.setAccessible(true);

			handle = class_worldBorder.newInstance();
			worldBorder = (WorldBorder) constructor1.newInstance(Bukkit.getWorlds().get(0));
			field.set(worldBorder, handle);

			worldBorder.setDamageAmount(0);
			worldBorder.setDamageBuffer(0);
			worldBorder.setWarningDistance(1);
			worldBorder.setWarningTime(10);
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException
				| SecurityException | IllegalArgumentException | InvocationTargetException | NoSuchFieldException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Enum getEnum(String name) {
		return Enum.valueOf((Class<Enum>) class_enum, name);
	}

	private static void sendPacket(Player player, Enum<?> action) {
		try {
			Object packet = constructor.newInstance(handle, action);
			method_sendPacket.invoke(field_playerConnection.get(method_getHandle.invoke(player)), packet);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			throw new RuntimeException("Error at constructor", e);
		}
	}

	private static String getVersion() {
		String packageName = Bukkit.getServer().getClass().getPackage().getName();
		return packageName.substring(packageName.lastIndexOf(".") + 1);
	}

	public static void sendToPlayer(Player p, Location center, double size) {
		worldBorder.setCenter(center);
		worldBorder.setSize(size);
		sendPacket(p, getEnum("INITIALIZE"));
	}

	public static void addSize(Player p, double oldsize, double newsize) {
		worldBorder.setSize(oldsize);
		worldBorder.setSize(newsize, (long) ((newsize - oldsize) * 5));
		sendPacket(p, getEnum("LERP_SIZE"));
	}
}
