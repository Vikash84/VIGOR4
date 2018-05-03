package org.jcvi.vigor.service;

import com.google.common.collect.Sets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jcvi.jillion.align.BlosumMatrices;
import org.jcvi.jillion.align.pairwise.PairwiseAlignmentBuilder;
import org.jcvi.jillion.align.pairwise.ProteinPairwiseSequenceAlignment;
import org.jcvi.jillion.core.DirectedRange;
import org.jcvi.jillion.core.Range;
import org.jcvi.jillion.core.residue.aa.ProteinSequence;
import org.jcvi.jillion.fasta.aa.ProteinFastaFileDataStore;
;
import org.jcvi.jillion.fasta.aa.ProteinFastaRecord;
import org.jcvi.vigor.component.MaturePeptideMatch;
import org.jcvi.vigor.component.ViralProtein;
import org.jcvi.vigor.service.exception.ServiceException;
import org.jcvi.vigor.utils.SequenceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class PeptideService implements PeptideMatchingService {

    /**
     * Maximum difference in amino acids length at beginning or end
     * for a peptide to be considered situated in the same spot on
     * the protein
     */
    private static final long PROXIMITY_MAX = 30L;
    private static final long MAX_GAP = 15L;
    private static Pattern productPattern = Pattern.compile("product\\s*=\\s*\"?(?<product>.*)\"?\\b");
    private static Logger LOGGER = LogManager.getLogger(PeptideService.class);

    private static class Scores {
        final double minidentity;
        final double mincoverage;
        final double minsimilarity;

        public Scores(double minidentity, double mincoverage, double minsimilarity) {
            this.minidentity = minidentity;
            this.mincoverage = mincoverage;
            this.minsimilarity = minsimilarity;
        }

        public static Scores of(double identity, double coverage, double similarity) {
            return new Scores(identity, coverage, similarity);
        }
    }


    private static class PeptideMatch {
        public final ProteinFastaRecord peptide;
        public final ProteinSequence protein;
        public final ProteinPairwiseSequenceAlignment alignment;

        public PeptideMatch(ProteinFastaRecord peptide, ProteinSequence protein, ProteinPairwiseSequenceAlignment alignment) {
            this.peptide = peptide;
            this.protein = protein;
            this.alignment = alignment;
        }

        public static PeptideMatch of(ProteinFastaRecord peptide, ProteinSequence protein, ProteinPairwiseSequenceAlignment alignment) {
            return new PeptideMatch(peptide, protein, alignment);
        }

    }

    private static class PeptideProfile {
        final private Map<String, Long> profile;
        final private long peptideLength;

        public PeptideProfile(Map<String, Long> profile, long length) {
            this.profile = profile;
            this.peptideLength = length;
        }

        public static PeptideProfile profileFromSequence(ProteinSequence sequence) {
            return new PeptideProfile(getKmerProfile(sequence), sequence.getLength());
        }
        // Return histogram of Kmers of length 1,2 and 3 to occurrences in the sequence.
        private static Map<String,Long> getKmerProfile(ProteinSequence sequence){
            String sequenceAsString = sequence.toString();
            int sequenceLength = sequenceAsString.length();

            // If the sequence is not evenly partitionable, we can ignore the extra at the end of the sequence,
            // because it would have already been counted by a smaller kmer.
            return Stream.of(1,2,3)
                  .flatMap(x->Stream.iterate(0, y -> y+x)
                                    .limit(sequenceLength/x )
                                    .map(i->sequenceAsString.substring(i,i+x)))
                  .collect(Collectors.groupingBy(String::valueOf, Collectors.counting()));
        }

        public Long get(String kmer) {
            return profile.get(kmer);
        }

        public Long getOrDefault(String kmer, Long defaultValue) {
            return profile.getOrDefault(kmer, defaultValue);
        }

        public Set<String> keySet() {
            return profile.keySet();
        }

        public long getPeptideLength() {
            return peptideLength;
        }
    }

    static final Comparator<PeptideMatch> byQueryAlignment = (a,b) -> {
        DirectedRange aDirectedRange = a.alignment.getSubjectRange();
        DirectedRange bDirectedRange = b.alignment.getSubjectRange();

        int result = aDirectedRange.getDirection().compareTo(bDirectedRange.getDirection());
        if (result == 0) {
            result = Range.Comparators.ARRIVAL.compare(aDirectedRange.getRange(),
                    bDirectedRange.getRange());
        }
        return result;
    };

    @Autowired
    public PeptideService() {

    }

    @Override
    public List<MaturePeptideMatch> findPeptides(ProteinSequence protein, File peptideDatabase) throws ServiceException {
        return findPeptides(protein, peptideDatabase, Scores.of(0.25d, .40d, .50d));
    }

    public List<MaturePeptideMatch> findPeptides(ProteinSequence protein, File peptideDatabase, Scores minscores) throws ServiceException {

        // filter
        Predicate<PeptideMatch> filterByScore = match -> {
            ProteinPairwiseSequenceAlignment alignment = match.alignment;
            double coverage = computeCoverage(match);
            double similiarity = computeSimilarity(match);
            LOGGER.debug("alignment for subject {} to query {} %identity {} min %identity {} %similarity {} min %similarity {} %coverage {}  min %coverage {}",
                    alignment.getGappedSubjectAlignment(),
                    alignment.getGappedQueryAlignment(),
                    alignment.getPercentIdentity(), minscores.minidentity,
                    similiarity, minscores.minsimilarity,
                    coverage, minscores.mincoverage);

            return alignment.getPercentIdentity() >= minscores.minidentity &&
                    similiarity >= minscores.minsimilarity &&
                    coverage >= minscores.mincoverage;
        };

        try (Stream<PeptideMatch> alignments = getAlignments(protein, peptideDatabase);) {

            List<Range> ranges = new ArrayList<>();

            // assumes sorted
            Function<PeptideMatch, Range> binByRange = match -> {
                Range subjectRange = match.alignment.getSubjectRange().asRange();

                if (ranges.size() != 0) {
                    Range rangeKey = ranges.get(ranges.size() - 1);

                    if (Math.abs(rangeKey.getBegin() - subjectRange.getBegin()) < PROXIMITY_MAX &&
                            Math.abs(rangeKey.getEnd() - subjectRange.getEnd()) < PROXIMITY_MAX) {
                        return rangeKey;
                    }
                }
                ranges.add(subjectRange);
                return subjectRange;
            };

            Map<Range, List<PeptideMatch>> alignmentsByRange = alignments.filter(filterByScore)
                                                                         .sorted(byQueryAlignment::compare)
                                                                         .collect(Collectors.groupingBy(binByRange));

            LOGGER.debug(() -> String.format("%s alignments after binning into %s ranges: %s",
                    alignmentsByRange.values().stream()
                                     .flatMap(l -> l.stream())
                                     .count(),
                    alignmentsByRange.keySet()
                                     .size(),
                    alignmentsByRange.keySet().stream()
                                     .sorted(Range.Comparators.ARRIVAL)
                                     .map(r -> String.format("%d-%d", r.getBegin(), r.getEnd()))
                                     .collect(Collectors.joining(", "))
            ));


            // TODO don't just use score, but also %identity %similarity %coverage as well as edges
            List<PeptideMatch> bestMatches = alignmentsByRange.keySet().stream()
                                           .sorted(Range.Comparators.ARRIVAL)
                                           .map((x) -> {
                                               PeptideMatch match = alignmentsByRange.get(x).stream()
                                                                                     .max(Comparator.comparing(p -> p.alignment.getScore()))
                                                                                     .get();
                                               LOGGER.debug("With score {} %identity {} returning range {}\nS>{}\nQ>{}\nP>{}",
                                                       match.alignment.getScore(),
                                                       match.alignment.getPercentIdentity(),
                                                       getRangeString(match),
                                                       match.alignment.getGappedSubjectAlignment(),
                                                       match.alignment.getGappedQueryAlignment(),
                                                       match.peptide.getSequence());
                                               return match;
                                           }).collect(Collectors.toList());

            List<MaturePeptideMatch> peptides = new ArrayList<>(alignmentsByRange.size());
            MaturePeptideMatch prev = null;
            MaturePeptideMatch current = null;
            Range currentRange;
            Range previousRange = null;
            for (PeptideMatch currentMatch: bestMatches) {
                // handle first one with a previous range before the start
                if (previousRange == null) {
                    previousRange = Range.of(Range.CoordinateSystem.RESIDUE_BASED, 0);
                }

                currentRange = currentMatch.alignment.getSubjectRange().getRange();
                current = peptideFromMatch(currentMatch);

                if (currentRange.getBegin() - previousRange.getEnd() > MAX_GAP) {
                    LOGGER.debug("difference between range [{}:{}] and [{}:{}] > max gap {}. Setting fuzzy edges",
                            previousRange.getBegin(), previousRange.getEnd(),
                            currentRange.getBegin(), currentRange.getEnd(),
                            MAX_GAP);
                    if (prev != null) {
                        prev.setFuzzyEnd(true);
                    }
                    current.setFuzzyBegin(true);
                } else if (currentRange.getBegin() != previousRange.getEnd() + 1) {
                    LOGGER.debug("Range [{}:{}] doesn't align to following range [{}:{}]. Adjusting edges",
                            previousRange.getBegin(), previousRange.getEnd(),
                            currentRange.getBegin(), currentRange.getEnd());
                    // the first one we just set to the beginning
                    if (prev == null) {
                        current.setProteinRange(currentRange.toBuilder().setBegin(0).build());
                    } else {
                        adjustPeptideEdges(protein, prev, current);
                    }
                }
                peptides.add(current);
                prev = current;
                previousRange = currentRange;
            }
            return peptides;
        } catch (IOException e) {
            throw new ServiceException(String.format("Problem finding peptide matches for sequence %s in database %s. got %s: %s",
                    protein, peptideDatabase, e.getClass().getSimpleName(), e.getMessage()), e);
        }
    }

    // TODO return new MaturePeptideMatch objects rather than altering the existing ones.
    private void adjustPeptideEdges(ProteinSequence subjectSequence, MaturePeptideMatch prev, MaturePeptideMatch current) {

        Range previousRange = prev.getProteinRange();
        Range currentRange = current.getProteinRange();
        LOGGER.debug("adjusting edges for\n[{}-{}] {}\n[{}-{}] {}",
                previousRange.getBegin(), previousRange.getEnd(),
                SequenceUtils.elipsedSequenceString(prev.getProtein().toBuilder().trim(previousRange).build(),30,30),
                currentRange.getBegin(), currentRange.getEnd(),
                SequenceUtils.elipsedSequenceString(current.getProtein().toBuilder().trim(currentRange).build(), 30, 30)
                );

        PeptideProfile previousReferenceProfile = PeptideProfile.profileFromSequence(prev.getReference().getSequence());
        PeptideProfile currentReferenceProfile = PeptideProfile.profileFromSequence(current.getReference().getSequence());

        long previousEnd = previousRange.getEnd();
        long currentBegin = currentRange.getBegin();

        long start, end;
        if (previousEnd >= currentBegin) {
            // Overlap
            start = currentBegin - 1;
            end = previousEnd;
        } else {
            // Gap
            start = previousEnd;
            end = currentBegin - 1;
        }
        Range testPreviousRange;
        Range testCurrentRange;
        ProteinSequence testPrevious;
        ProteinSequence testCurrent;
        Range[] bestRange = {previousRange, currentRange};
        double bestScore = 0;
        double testScore = 0;
        for (; start <= end; start++) {
            // profile and score new sequences
            testPreviousRange = previousRange.toBuilder().setEnd(start).build();
            testCurrentRange = currentRange.toBuilder().setBegin(start+1).build();
            // now we need the sequence for the new test ranges
            testPrevious = subjectSequence.toBuilder().trim(testPreviousRange).build();
            testCurrent = subjectSequence.toBuilder().trim(testCurrentRange).build();

            testScore = scorePeptideByProfile(testPrevious, previousReferenceProfile) + scorePeptideByProfile(testCurrent, currentReferenceProfile);
            if (testScore > bestScore) {
                bestRange[0] = testPreviousRange;
                bestRange[1] = testCurrentRange;
                bestScore = testScore;
            }
        }

        Range bestPreviousRange = bestRange[0];
        Range bestCurrentRange = bestRange[1];
        // adjust previous and current
        prev.setProteinRange(bestPreviousRange);
        Range referenceRange = prev.getReferenceRange();
        prev.setReferenceRange(referenceRange.toBuilder().setEnd(referenceRange.getEnd() + (bestPreviousRange.getEnd() - previousEnd)).build());
        // TODO is this correct?
        prev.setFuzzyEnd(false);

        current.setProteinRange(bestCurrentRange);
        referenceRange = current.getReferenceRange();
        current.setReferenceRange(referenceRange.toBuilder().setBegin(referenceRange.getBegin() + (bestCurrentRange.getBegin() - currentBegin)).build());
        // TODO
        current.setFuzzyBegin(false);
    }

    private MaturePeptideMatch peptideFromMatch (PeptideMatch match) {

        ViralProtein referenceProtein = new ViralProtein();
        referenceProtein.setSequence(match.peptide.getSequence());
        referenceProtein.setDefline(String.join(" ", ">" + match.peptide.getId(), match.peptide.getComment()));
        Matcher m = productPattern.matcher(referenceProtein.getDefline());
        if (m.find()) {
            referenceProtein.setProduct(m.group("product"));
        }
        referenceProtein.setProteinID(match.peptide.getId());


        return MaturePeptideMatch.of(match.protein,
                referenceProtein,
                match.alignment.getSubjectRange().asRange(),
                match.alignment.getQueryRange().asRange());
    }

    private static String getRangeString(PeptideMatch match) {
        Range queryRange = match.alignment.getQueryRange().asRange();
        Range subjectRange = match.alignment.getSubjectRange().asRange();
        return String.format("%s%s-%s%s",
                queryRange.getBegin() > 0 ? "<" : "", subjectRange.getBegin(Range.CoordinateSystem.RESIDUE_BASED),
                queryRange.getEnd() < match.peptide.getLength() - 1 ? ">" : "", subjectRange.getEnd(Range.CoordinateSystem.RESIDUE_BASED));
    }

    private double computeSimilarity(PeptideMatch match) {
        ProteinSequence querySequence = match.alignment.getGappedQueryAlignment();
        return   ((double)(match.alignment.getAlignmentLength() - querySequence.getNumberOfGaps() - match.alignment.getNumberOfMismatches()) / (double) match.peptide.getSequence().getLength());
    }

    private double computeCoverage(PeptideMatch match) {
        return Math.max(
                (double) match.alignment.getQueryRange().getLength()/ (double) match.protein.getLength(),
                (double) match.alignment.getSubjectRange().getLength() / (double) match.peptide.getSequence().getLength()
        );
    }

    private double scorePeptideByProfile(ProteinSequence peptide, PeptideProfile referenceProfile) {
        return scoreProfile(PeptideProfile.profileFromSequence(peptide), referenceProfile);
    }
    
    private double scoreProfile(PeptideProfile subjectProfile, PeptideProfile referenceProfile) {
        Set<String> peptideKeys = subjectProfile.keySet();
        Set<String> profileKeys = referenceProfile.keySet();
        long mismatches = 0;
        long matches = 0;
        long peptideCount;
        long profileCount;
        Set<String> commonKeys = Sets.intersection(peptideKeys, profileKeys);
        for (String key: commonKeys) {
            peptideCount = subjectProfile.get(key);
            profileCount = referenceProfile.get(key);
            matches += Math.min(peptideCount, profileCount);
            mismatches += Math.abs(peptideCount - profileCount);
        }

        //TODO account for gaps
        for (String key: Sets.symmetricDifference(peptideKeys, profileKeys)) {
            mismatches += subjectProfile.getOrDefault(key, referenceProfile.get(key));
        }

        double score = (referenceProfile.getPeptideLength() * (2 * matches)) / ((Math.abs(subjectProfile.getPeptideLength() - referenceProfile.getPeptideLength())) + ((2 * matches) + mismatches));
        return score;
    }

    Stream<PeptideMatch> getAlignments(ProteinSequence protein, File peptideDatabase) throws IOException {

        LOGGER.info("finding alignments in {} for seq {}", peptideDatabase, SequenceUtils.elipsedSequenceString(protein, 40,20));

        ProteinFastaFileDataStore peptideDataStore = ProteinFastaFileDataStore.fromFile(peptideDatabase);
        // TODO configurable gap penalties and blosum matrix
        return peptideDataStore.records()
                               .map(record -> PeptideMatch.of(record,
                                       protein,
                                       PairwiseAlignmentBuilder.createProtienAlignmentBuilder(
                                               record.getSequence(),
                                               protein,
                                               BlosumMatrices.blosum40())
                                                               .useLocalAlignment(true)
                                                               .gapPenalty(-16F,-8F)
                                                               .build())
                               );
    }


}
