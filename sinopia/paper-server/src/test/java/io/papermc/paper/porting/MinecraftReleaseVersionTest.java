package io.papermc.paper.porting;

import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@AllFeatures
class MinecraftReleaseVersionTest {
    @Test
    void runtimeUsesTheActualReleaseVersionAndProtocol() {
        // Verify Minecraft's runtime metadata, not the Gradle artifact name.
        WorldVersion version = SharedConstants.getCurrentVersion();
        assertEquals("26.3", version.id());
        assertEquals("26.3", version.name());
        assertEquals(5023, version.dataVersion().version());
        assertEquals("main", version.dataVersion().series());
        assertEquals(777, version.protocolVersion());
        assertEquals(777, SharedConstants.getProtocolVersion());
        assertEquals(5023, SharedConstants.WORLD_VERSION);
        assertEquals(777, SharedConstants.RELEASE_NETWORK_PROTOCOL_VERSION);
        assertEquals(PackFormat.of(97, 1), version.packVersion(PackType.CLIENT_RESOURCES));
        assertEquals(PackFormat.of(121, 0), version.packVersion(PackType.SERVER_DATA));
        assertFalse(SharedConstants.SNAPSHOT);
        assertTrue(version.stable());
    }
}
