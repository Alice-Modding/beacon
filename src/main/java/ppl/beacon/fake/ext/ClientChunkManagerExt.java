package ppl.beacon.fake.ext;

import ppl.beacon.fake.chunk.FakeChunkManager;
//import ppl.beacon.VisibleChunksTracker;

public interface ClientChunkManagerExt {
    FakeChunkManager getFakeChunkManager();
    //VisibleChunksTracker getRealChunksTracker();
    void onFakeChunkAdded(int x, int z);
    void onFakeChunkRemoved(int x, int z, boolean willBeReplaced);
}
