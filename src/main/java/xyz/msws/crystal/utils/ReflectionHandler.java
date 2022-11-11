package xyz.msws.crystal.utils;

import org.bukkit.Bukkit;

public class ReflectionHandler {
	private static final String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];

	public static Class<?> getClass(ReflectionClassType type, String name) {
		try {
			return Class.forName(type.toString() + version + "." + name);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

	public enum ReflectionClassType {
		NMS("net.minecraft.server."), OBC("org.bukkit.craftbukkit.");

		private final String text;

		ReflectionClassType(final String text) {
			this.text = text;
		}

		@Override
		public String toString() {
			return text;
		}
	}
}