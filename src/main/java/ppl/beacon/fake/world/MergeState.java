package ppl.beacon.fake.world;

import ppl.beacon.utils.RegionPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class MergeState {
    protected volatile MergeStage stage = MergeStage.BlockedByOtherMerge;
    protected RegionPos activeRegion;

    protected final Queue<CopyJob> activeJobs = new ArrayDeque<>();
    protected final List<CopyJob> finishedJobs = new ArrayList<>();
}
