package ppl.beacon.fake.ext;

import ppl.beacon.fake.FakeManager;
import ppl.beacon.fake.chunk.VisibleChunksTracker;

public interface ClientChunkManagerExt {
    FakeManager beacon$getFakeChunkManager();
    VisibleChunksTracker beacon$getRealChunksTracker();
    void beacon$onFakeChunkAdded(int x, int z);
    void beacon$onFakeChunkRemoved(int x, int z, boolean willBeReplaced);
}
