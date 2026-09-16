package dev.futuretech.client.jei;

import net.neoforged.fml.ModList;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** JEI finds plugins through the mod scan data, so the annotation has to survive compilation. */
class JeiPluginScanTest {
    @Test
    void pluginAnnotationIsInTheModScanData() {
        var wanted = Type.getType("Lmezz/jei/api/JeiPlugin;");
        var found = ModList.get().getAllScanData().stream()
                .flatMap(data -> data.getAnnotations().stream())
                .filter(a -> a.annotationType().equals(wanted))
                .map(a -> a.memberName())
                .toList();
        // By name: the JEI API is compile-time only, so the plugin class cannot load in the unit tests.
        assertTrue(found.contains("dev.futuretech.client.jei.FutureTechJeiPlugin"), found.toString());
    }
}
