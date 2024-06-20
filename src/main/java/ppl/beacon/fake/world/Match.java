package ppl.beacon.fake.world;

import it.unimi.dsi.fastutil.longs.LongSet;

public record Match(LongSet matching, LongSet mismatching) {

    public void addAll(Match other) {
        mismatching.addAll(other.mismatching);
        matching.addAll(other.matching);
    }

    public LongSet getMatching() {
        return matching;
    }

    public LongSet getMisMatching() {
        return mismatching;
    }
}
