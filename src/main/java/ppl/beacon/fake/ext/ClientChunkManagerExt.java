package ppl.beacon.fake.ext;

import ppl.beacon.fake.chunk.FakeChunkManager;
import ppl.beacon.fake.chunk.VisibleChunksTracker;

public interface ClientChunkManagerExt {
    FakeChunkManager beacon$getFakeChunkManager();
    VisibleChunksTracker beacon$getRealChunksTracker();
    void beacon$onFakeChunkAdded(int x, int z);
    void beacon$onFakeChunkRemoved(int x, int z, boolean willBeReplaced);
}
