package dev.futuretech.block;

import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every cable is metal, so a pickaxe mines every cable faster. None of them requires the right
 * tool to drop, which is why the item and fluid cables went four versions missing from the tag
 * without anyone noticing: the only thing it costs is the speed. This is what noticed.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class CablePickaxeTagTest {
    @Test
    void everyCableIsMinedByThePickaxe(MinecraftServer server) throws Exception {
        Set<String> tagged = new LinkedHashSet<>();
        try (var stream = getClass().getResourceAsStream("/data/minecraft/tags/block/mineable/pickaxe.json")) {
            assertNotNull(stream, "the pickaxe tag ships");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var value : json.getAsJsonArray("values")) tagged.add(value.getAsString());
        }
        List<String> cables = new ArrayList<>();
        for (var block : BuiltInRegistries.BLOCK) {
            if (block instanceof AbstractCableBlock) cables.add(BuiltInRegistries.BLOCK.getKey(block).toString());
        }
        assertFalse(cables.isEmpty(), "the cables are registered");
        for (String cable : cables) assertTrue(tagged.contains(cable), cable + " is missing from mineable/pickaxe");
    }
}
