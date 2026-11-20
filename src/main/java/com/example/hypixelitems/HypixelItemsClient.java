package com.example.hypixelitems;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HypixelItemsClient implements ClientModInitializer {
    private static final String API = "https://api.hypixel.net/v2/resources/skyblock/items";
    private static final Map<String, JsonObject> ITEMS = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("hypixel")
                .then(ClientCommandManager.literal("reload")
                    .executes(ctx -> {
                        fetchItems();
                        say("Downloading Hypixel SkyBlock item definitions...");
                        return 1;
                    }))
                .then(ClientCommandManager.literal("give")
                    .then(ClientCommandManager.argument("item_id", StringArgumentType.word())
                        .executes(ctx -> giveItem(StringArgumentType.getString(ctx, "item_id")))))
                .then(ClientCommandManager.literal("search")
                    .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> search(StringArgumentType.getString(ctx, "name")))))
                .then(ClientCommandManager.literal("help")
                    .executes(ctx -> {
                        say("/hypixel reload  - download/update item data");
                        say("/hypixel search <name>  - search item IDs and names");
                        say("/hypixel give <ITEM_ID>  - give a local item representation");
                        return 1;
                    }))
            );
        });
        fetchItems();
    }

    private static void fetchItems() {
        Thread thread = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(API))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "HypixelItemsFabric/1.0")
                    .GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    say("Hypixel API returned HTTP " + response.statusCode());
                    return;
                }
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonArray items = root.getAsJsonArray("items");
                ITEMS.clear();
                for (JsonElement element : items) {
                    JsonObject item = element.getAsJsonObject();
                    if (item.has("id") && item.has("name")) {
                        ITEMS.put(item.get("id").getAsString().toUpperCase(Locale.ROOT), item);
                    }
                }
                loaded = true;
                say("Loaded " + ITEMS.size() + " Hypixel item definitions. Try /hypixel search Hyperion");
            } catch (Exception e) {
                say("Could not download item data: " + e.getMessage());
            }
        }, "Hypixel-Items-API");
        thread.setDaemon(true);
        thread.start();
    }

    private static int search(String query) {
        if (!loaded) {
            say("Item database is still loading. Try /hypixel reload in a moment.");
            return 0;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        int found = 0;
        for (Map.Entry<String, JsonObject> entry : ITEMS.entrySet()) {
            JsonObject item = entry.getValue();
            String name = item.get("name").getAsString();
            if (name.toLowerCase(Locale.ROOT).contains(needle) || entry.getKey().toLowerCase(Locale.ROOT).contains(needle)) {
                say(entry.getKey() + "  -  " + name + " [" + getString(item, "tier", "UNKNOWN") + "]");
                if (++found >= 12) break;
            }
        }
        say(found == 0 ? "No matching items found." : "Use /hypixel give <ITEM_ID> to get one.");
        return found;
    }

    private static int giveItem(String id) {
        if (!loaded) {
            say("Item database is not ready. Use /hypixel reload and wait.");
            return 0;
        }
        JsonObject data = ITEMS.get(id.toUpperCase(Locale.ROOT));
        if (data == null) {
            say("Unknown item ID. Search first: /hypixel search " + id);
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            say("You must be in a single-player world.");
            return 0;
        }

        String material = getString(data, "material", "PAPER").toLowerCase(Locale.ROOT);
        ResourceLocation key = ResourceLocation.withDefaultNamespace(material);
        Item item = BuiltInRegistries.ITEM.get(key);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            item = net.minecraft.world.item.Items.PAPER;
        }

        ItemStack stack = new ItemStack(item);
        String name = getString(data, "name", id);
        String tier = getString(data, "tier", "UNKNOWN");
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("[" + tier + "] " + name));

        boolean added = mc.player.getInventory().add(stack);
        if (!added) mc.player.drop(stack, false);

        say("Gave " + name + " (" + id + ").");
        say("Note: this is a vanilla-material representation; Hypixel-only abilities and exact 3D textures are not recreated yet.");
        sayStats(data);
        return 1;
    }

    private static void sayStats(JsonObject data) {
        if (!data.has("stats") || !data.get("stats").isJsonObject()) return;
        JsonObject stats = data.getAsJsonObject("stats");
        StringBuilder line = new StringBuilder("Stats: ");
        for (Map.Entry<String, JsonElement> stat : stats.entrySet()) {
            if (line.length() > 7) line.append(", ");
            line.append(stat.getKey()).append(" ").append(stat.getValue().getAsString());
        }
        say(line.toString());
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : fallback;
    }

    private static void say(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.execute(() -> mc.player.displayClientMessage(Component.literal("[Hypixel Items] " + message), false));
        }
    }
}
