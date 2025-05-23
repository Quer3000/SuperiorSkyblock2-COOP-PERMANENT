package com.bgsoftware.superiorskyblock.core.io;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.core.ServerVersion;
import com.bgsoftware.superiorskyblock.core.logging.Log;

import java.io.File;
import java.io.InputStream;

public class Resources {

    private static final SuperiorSkyblockPlugin plugin = SuperiorSkyblockPlugin.getPlugin();

    private Resources() {

    }

    public static void copyResource(String resourcePath) {
        String fixedPath = resourcePath + ".jar";
        File dstFile = new File(plugin.getDataFolder(), fixedPath);

        if (dstFile.exists())
            //noinspection ResultOfMethodCallIgnored
            dstFile.delete();

        plugin.saveResource(resourcePath, true);

        File file = new File(plugin.getDataFolder(), resourcePath);
        //noinspection ResultOfMethodCallIgnored
        file.renameTo(dstFile);
    }

    public static void saveResource(String resourcePath) {
        saveResource(resourcePath, resourcePath);
    }

    public static void saveResource(String destination, String resourcePath) {
        try {
            for (ServerVersion serverVersion : ServerVersion.getByOrder()) {
                String version = serverVersion.name().substring(1);
                if (resourcePath.endsWith(".yml") && plugin.getResource(resourcePath.replace(".yml", version + ".yml")) != null) {
                    resourcePath = resourcePath.replace(".yml", version + ".yml");
                    break;
                } else if (resourcePath.endsWith(".schematic") && plugin.getResource(resourcePath.replace(".schematic", version + ".schematic")) != null) {
                    resourcePath = resourcePath.replace(".schematic", version + ".schematic");
                    break;
                }
            }

            File file = new File(plugin.getDataFolder(), resourcePath);
            plugin.saveResource(resourcePath, true);

            if (!destination.equals(resourcePath)) {
                File dest = new File(plugin.getDataFolder(), destination);
                //noinspection ResultOfMethodCallIgnored
                file.renameTo(dest);
            }
        } catch (Exception error) {
            Log.entering("ENTER", destination, resourcePath);
            Log.error(error, "An unexpected error occurred while saving resource:");
        }
    }

    public static InputStream getResource(String resourcePath) {
        String[] suffixAndPath = getPathAndSuffix(resourcePath);
        String resourcePathNoSuffix = suffixAndPath[0];
        String suffix = suffixAndPath[1];

        try {
            for (ServerVersion serverVersion : ServerVersion.getByOrder()) {
                String version = serverVersion.name().substring(1);

                String realPath = resourcePathNoSuffix + version + suffix;

                try (InputStream resource = plugin.getResource(realPath)) {
                    if (resource != null) {
                        resourcePath = realPath;
                        break;
                    }
                }
            }

            return plugin.getResource(resourcePath);
        } catch (Exception error) {
            Log.entering("ENTER", resourcePath);
            Log.error(error, "An unexpected error occurred while retrieving resource:");
            return null;
        }
    }

    private static String[] getPathAndSuffix(String name) {
        int lastIndex = name.lastIndexOf('.');
        String suffix = lastIndex == -1 || lastIndex + 1 >= name.length() ? "" : name.substring(lastIndex + 1);
        return suffix.isEmpty() ? new String[]{name, ""} : new String[]{name.substring(0, name.length() - suffix.length()), suffix};
    }

}
