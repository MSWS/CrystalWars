package xyz.msws.crystal.utils;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

public class NBT {
	// Code provided by https://gist.github.com/vemacs/6cd43a50950796458984

	private static String serverVersion;
	private static Method getHandle;
	private static Method getNBTTag;
	private static Class<?> nmsEntityClass;
	private static Class<?> nbtTagClass;
	private static Method c;
	private static Method setInt;
	private static Method f;

	public void setNBT(String tagName, Entity entity, boolean enabled) {
		try {
			if (serverVersion == null) {
				String name = Bukkit.getServer().getClass().getName();
				String[] parts = name.split("\\.");
				serverVersion = parts[3];
			}
			if (getHandle == null) {
				Class<?> craftEntity = Class.forName("org.bukkit.craftbukkit." + serverVersion + ".entity.CraftEntity");
				getHandle = craftEntity.getDeclaredMethod("getHandle");
				getHandle.setAccessible(true);
			}
			Object nmsEntity = getHandle.invoke(entity);
			if (nmsEntityClass == null) {
				nmsEntityClass = Class.forName("net.minecraft.server." + serverVersion + ".Entity");
			}
			if (getNBTTag == null) {
				getNBTTag = nmsEntityClass.getDeclaredMethod("getNBTTag");
				getNBTTag.setAccessible(true);
			}
			Object tag = getNBTTag.invoke(nmsEntity);
			if (nbtTagClass == null) {
				nbtTagClass = Class.forName("net.minecraft.server." + serverVersion + ".NBTTagCompound");
			}
			if (tag == null) {
				tag = nbtTagClass.newInstance();
			}
			if (c == null) {
				c = nmsEntityClass.getDeclaredMethod("c", nbtTagClass);
				c.setAccessible(true);
			}
			c.invoke(nmsEntity, tag);
			if (setInt == null) {
				setInt = nbtTagClass.getDeclaredMethod("setInt", String.class, Integer.TYPE);
				setInt.setAccessible(true);
			}
			int value = enabled ? 1 : 0;
			setInt.invoke(tag, tagName, value);
			if (f == null) {
				f = nmsEntityClass.getDeclaredMethod("f", nbtTagClass);
				f.setAccessible(true);
			}
			f.invoke(nmsEntity, tag);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
