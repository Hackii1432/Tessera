package io.papermc.paper.porting;

import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@AllFeatures
class MinecraftRc3VersionTest {
    @Test
    void runtimeUsesTheActualRc3VersionAndProtocol() {
        // Verify Minecraft's runtime metadata, not the Gradle artifact name.
        WorldVersion version = SharedConstants.getCurrentVersion();
        assertEquals("26.3-rc-3", version.id());
        assertEquals("26.3 Release Candidate 3", version.name());
        assertEquals(5022, version.dataVersion().version());
        assertEquals((1 << 30) | 338, version.protocolVersion());
        assertEquals(5022, SharedConstants.WORLD_VERSION);
        assertEquals(338, SharedConstants.SNAPSHOT_NETWORK_PROTOCOL_VERSION);
        assertFalse(version.stable());
    }
}
