package com.stylex.chrome;

import com.google.common.base.Charsets;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.BitSet;

public class CoverageInfo {

    private transient final BitSet coverageBitSet;
    private transient final String scriptId;
    private transient final String scriptContents;
    private final String scriptContentsHash;
    private final int scriptLength;
    private final String scriptURL;
    private int numberOfCharactersCovered;

    public CoverageInfo(String scriptId, String scriptURL, String scriptBody) {
        this(scriptId, scriptURL, scriptBody, new BitSet(scriptBody.length()));
    }

    private CoverageInfo(String scriptId, String scriptURL, String scriptBody, BitSet coverageBitSet) {
        this.scriptId = scriptId;
        this.scriptContents = scriptBody;
        this.coverageBitSet = coverageBitSet;
        this.scriptContentsHash = DigestUtils.sha1Hex(scriptBody.getBytes(Charsets.UTF_8));
        this.scriptLength = scriptBody.length();
        this.numberOfCharactersCovered = coverageBitSet.cardinality();
        this.scriptURL = scriptURL;
    }

    public String getScriptId() {
        return scriptId;
    }

    public String getScriptContents() {
        return scriptContents;
    }

    public int getScriptLength() {
        return this.scriptLength;
    }

    public String getScriptContentsHash() {
        return this.scriptContentsHash;
    }

    public String getScriptURL() {
        return this.scriptURL;
    }

    @Override
    public String toString() {
        return scriptContents;
    }

    public void setCovered(int startIndex, int endIndex) {
        coverageBitSet.set(startIndex, endIndex);
        this.numberOfCharactersCovered = coverageBitSet.cardinality();
    }

    public void clear(int startIndex, int endIndex) {
        coverageBitSet.clear(startIndex, endIndex);
        this.numberOfCharactersCovered = coverageBitSet.cardinality();
    }

    public CoverageInfo clone() {
        BitSet clone = new BitSet(scriptContents.length());
        clone.or(coverageBitSet);
        return new CoverageInfo(scriptId, scriptURL, scriptContents, clone);
    }

    public int getNumberOfCharactersCovered() {
        return this.numberOfCharactersCovered;
    }

    public void or(CoverageInfo newCoverageInfo) {
        this.coverageBitSet.or(newCoverageInfo.coverageBitSet);
        this.numberOfCharactersCovered = this.coverageBitSet.cardinality();
    }
}
