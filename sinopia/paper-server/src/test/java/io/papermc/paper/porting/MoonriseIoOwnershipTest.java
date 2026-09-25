package io.papermc.paper.porting;

import ca.spottedleaf.concurrentutil.completable.Completable;
import ca.spottedleaf.concurrentutil.executor.Cancellable;
import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionFileType;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Normal
class MoonriseIoOwnershipTest {
    private static final class Controller extends RegionDataController {
        final ConcurrentLinkedQueue<Runnable> queue;
        final CompoundTag source = new CompoundTag();
        final AtomicReference<CompoundTag> lastRead = new AtomicReference<>();
        final AtomicReference<CompoundTag> lastWrite = new AtomicReference<>();
        final AtomicInteger reads = new AtomicInteger();

        Controller() {
            this(new ConcurrentLinkedQueue<>());
        }

        private Controller(ConcurrentLinkedQueue<Runnable> queue) {
            super(RegionFileType.CHUNK_DATA, executor(queue), executor(queue));
            this.queue = queue;
            CompoundTag child = new CompoundTag();
            child.putInt("value", 42);
            this.source.put("child", child);
        }

        private static PrioritisedExecutor executor(ConcurrentLinkedQueue<Runnable> queue) {
            PrioritisedExecutor executor = mock(PrioritisedExecutor.class);
            when(executor.createTask(any(Runnable.class), any(Priority.class))).thenAnswer(call -> task(queue, call.getArgument(0)));
            return executor;
        }

        private static PrioritisedExecutor.PrioritisedTask task(ConcurrentLinkedQueue<Runnable> queue, Runnable run) {
            PrioritisedExecutor.PrioritisedTask task = mock(PrioritisedExecutor.PrioritisedTask.class);
            when(task.queue()).thenAnswer(call -> queue.add(run));
            return task;
        }

        @Override
        public PrioritisedExecutor.PrioritisedTask createRegionIoTask(int x, int z, Runnable run, Priority priority) {
            return task(this.queue, run);
        }

        @Override
        public ReadData readData(int x, int z) {
            this.reads.incrementAndGet();
            CompoundTag data = this.source.copy();
            this.lastRead.set(data);
            return new ReadData(ReadData.ReadResult.SYNC_READ, null, data, 0);
        }

        @Override
        public CompoundTag finishRead(int x, int z, ReadData data) {
            return data.syncRead();
        }

        @Override
        public WriteData startWrite(int x, int z, CompoundTag data) {
            return new WriteData(data, WriteData.WriteResult.DELETE, null, null);
        }

        @Override
        public void finishWrite(int x, int z, WriteData data) {
            this.lastWrite.set(data.input());
        }

        @Override
        public RegionFileStorage getCache() {
            return mock(RegionFileStorage.class);
        }

        void drain() {
            for (int count = 0; count < 10000; count++) {
                Runnable next = this.queue.poll();
                if (next == null) return;
                next.run();
            }
            fail("I/O tasks did not settle");
        }

        ServerLevel world() {
            ServerLevel world = mock(ServerLevel.class, RETURNS_DEEP_STUBS);
            when(world.moonrise$getChunkDataController()).thenReturn(this);
            var config = mock(io.papermc.paper.configuration.WorldConfiguration.class);
            config.chunks = mock(io.papermc.paper.configuration.WorldConfiguration.Chunks.class);
            when(world.paperConfig()).thenReturn(config);
            return world;
        }
    }

    private static Cancellable read(ServerLevel world, java.util.function.BiConsumer<CompoundTag, Throwable> callback) {
        return MoonriseRegionFileIO.loadDataAsync(world, 0, 0, RegionFileType.CHUNK_DATA, callback, false, Priority.NORMAL);
    }

    @Test
    void singleReaderReceivesExclusiveDataAndTaskIsReleased() {
        Controller controller = new Controller();
        ServerLevel world = controller.world();
        AtomicReference<CompoundTag> received = new AtomicReference<>();
        read(world, (data, failure) -> received.set(data));
        assertEquals(1, controller.getTotalWorkingTasks());
        controller.drain();
        assertSame(controller.lastRead.get(), received.get());
        received.get().getCompoundOrEmpty("child").putInt("value", 7);
        assertEquals(42, controller.source.getCompoundOrEmpty("child").getIntOr("value", -1));
        assertFalse(controller.hasTasks());
    }

    @Test
    void multipleReadersCannotMutateEachOthersNbt() {
        Controller controller = new Controller();
        ServerLevel world = controller.world();
        AtomicReference<CompoundTag> first = new AtomicReference<>();
        AtomicReference<CompoundTag> second = new AtomicReference<>();
        read(world, (data, failure) -> {
            first.set(data);
            data.getCompoundOrEmpty("child").putInt("value", 7);
        });
        read(world, (data, failure) -> second.set(data));
        controller.drain();
        assertNotSame(first.get(), second.get());
        assertNotSame(controller.lastRead.get(), first.get());
        assertEquals(42, second.get().getCompoundOrEmpty("child").getIntOr("value", -1));
        assertFalse(controller.hasTasks());
    }

    @Test
    void cancellingAllReadersReleasesTaskWithoutReadingStorage() {
        Controller controller = new Controller();
        ServerLevel world = controller.world();
        AtomicInteger delivered = new AtomicInteger();
        Cancellable one = read(world, (data, failure) -> delivered.incrementAndGet());
        Cancellable two = read(world, (data, failure) -> delivered.incrementAndGet());
        assertTrue(one.cancel());
        assertTrue(two.cancel());
        controller.drain();
        assertEquals(0, delivered.get());
        assertEquals(0, controller.reads.get());
        assertFalse(controller.hasTasks());
    }

    @Test
    void pendingWriteRemainsIsolatedFromReaders() {
        Controller controller = new Controller();
        ServerLevel world = controller.world();
        AtomicReference<CompoundTag> first = new AtomicReference<>();
        read(world, (data, failure) -> first.set(data));
        Completable<CompoundTag> saved = new Completable<>();
        MoonriseRegionFileIO.scheduleSave(world, 0, 0, saved, null, RegionFileType.CHUNK_DATA, Priority.NORMAL);
        CompoundTag write = controller.source.copy();
        write.getCompoundOrEmpty("child").putInt("value", 99);
        AtomicReference<CompoundTag> second = new AtomicReference<>();
        read(world, (data, failure) -> {
            second.set(data);
            data.getCompoundOrEmpty("child").putInt("value", 12);
        });
        saved.complete(write);
        controller.drain();
        assertEquals(42, first.get().getCompoundOrEmpty("child").getIntOr("value", -1));
        assertNotSame(write, second.get());
        assertEquals(99, controller.lastWrite.get().getCompoundOrEmpty("child").getIntOr("value", -1));
        assertFalse(controller.hasTasks());
    }

    @Test
    void callbackCanRequestANewReadAfterCompletion() {
        Controller controller = new Controller();
        ServerLevel world = controller.world();
        AtomicInteger delivered = new AtomicInteger();
        read(world, (data, failure) -> {
            delivered.incrementAndGet();
            read(world, (next, error) -> delivered.incrementAndGet());
        });
        controller.drain();
        assertEquals(2, delivered.get());
        assertEquals(2, controller.reads.get());
        assertFalse(controller.hasTasks());
    }

    @Test
    void simultaneousReadersReceiveIndependentMutableResults() throws Exception {
        Controller controller = new Controller();
        ServerLevel world = controller.world();
        var received = new ConcurrentLinkedQueue<CompoundTag>();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<java.util.concurrent.Callable<Void>>();
            for (int index = 0; index < 100; index++) {
                tasks.add(() -> {
                    read(world, (data, failure) -> received.add(data));
                    return null;
                });
            }
            for (var task : executor.invokeAll(tasks)) task.get();
        }
        controller.drain();
        assertEquals(100, received.size());
        var identities = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<CompoundTag, Boolean>());
        identities.addAll(received);
        assertEquals(100, identities.size());
        assertFalse(controller.hasTasks());
    }
}
