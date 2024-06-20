package ppl.beacon.fake.world;

public enum MergeStage {
    BlockedByOtherMerge,
    BlockedByPreviouslyQueuedWrites,
    WaitForPreviouslyQueuedWrites,
    Idle,
    WaitForRegion,
    Copying,
    Syncing,
    WriteTargetMeta,
    SyncTargetMeta,
    DeleteSourceMeta,
    DeleteSourceStorage,
    Done,
}
