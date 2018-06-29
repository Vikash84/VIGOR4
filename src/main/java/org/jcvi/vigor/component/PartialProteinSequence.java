package org.jcvi.vigor.component;


import org.jcvi.jillion.core.residue.aa.ProteinSequence;

public class PartialProteinSequence {
    final boolean partial3p;
    final boolean partial5p;
    final ProteinSequence sequence;

    private PartialProteinSequence(ProteinSequence sequence, boolean partial3p, boolean partial5p) {
        this.sequence = sequence;
        this.partial3p = partial3p;
        this.partial5p = partial5p;
    }

    public static PartialProteinSequence of (ProteinSequence sequence, boolean partial3p, boolean partial5p) {
        return new PartialProteinSequence(sequence, partial3p, partial5p);
    }

    public boolean isPartial3p() {
        return partial3p;
    }

    public boolean isPartial5p() {
        return partial5p;
    }

    public ProteinSequence getSequence() {
        return sequence;
    }
}