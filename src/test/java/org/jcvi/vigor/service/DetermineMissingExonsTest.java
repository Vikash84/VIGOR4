package org.jcvi.vigor.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jcvi.jillion.align.AminoAcidSubstitutionMatrix;
import org.jcvi.jillion.align.BlosumMatrices;
import org.jcvi.jillion.align.pairwise.PairwiseAlignmentBuilder;
import org.jcvi.jillion.align.pairwise.ProteinPairwiseSequenceAlignment;
import org.jcvi.jillion.core.DirectedRange;
import org.jcvi.jillion.core.Direction;
import org.jcvi.jillion.core.Range;
import org.jcvi.jillion.core.residue.aa.ProteinSequence;
import org.jcvi.jillion.core.residue.aa.ProteinSequenceBuilder;
import org.jcvi.jillion.core.residue.nt.NucleotideSequence;
import org.jcvi.jillion.core.residue.nt.NucleotideSequenceBuilder;
import org.jcvi.vigor.testing.category.Fast;
import org.jcvi.vigor.exception.VigorException;
import org.jcvi.vigor.testing.category.ReferenceDatabase;
import org.jcvi.vigor.utils.ConfigurationParameters;
import org.jcvi.vigor.utils.VigorConfiguration;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.jcvi.vigor.Application;
import org.jcvi.vigor.component.Alignment;
import org.jcvi.vigor.component.Exon;
import org.jcvi.vigor.component.Model;
import org.jcvi.vigor.utils.VigorTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@Category({Fast.class})
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = Application.class)
public class DetermineMissingExonsTest {

    @Autowired
    private ModelGenerationService modelGenerationService;
    @Autowired
    private DetermineMissingExons determineMissingExons;
    @Autowired
    private ViralProteinService viralProteinService;
    @Autowired
    private VigorInitializationService initializationService;


    @Test
    @Category({ReferenceDatabase.class})
    public void findMissingExonsWithSpliceFormPresent () throws VigorException {

        VigorConfiguration config = initializationService.mergeConfigurations(initializationService.getDefaultConfigurations());
        File virusGenomeSeqFile = new File(getClass().getResource("/vigorUnitTestInput/sequence_flua.fasta").getFile());
        File alignmentOutput = new File(getClass().getResource("/vigorUnitTestInput/sequence_flua_alignmentTest.txt").getFile());
        String refDBPath = config.get(ConfigurationParameters.ReferenceDatabasePath);
        assertThat("Reference database path must be set", refDBPath, is(notNullValue()));
        String referenceDB = Paths.get(refDBPath, "flua_db").toString();
        List<Alignment> alignments = VigorTestUtils.getAlignments(virusGenomeSeqFile, referenceDB, alignmentOutput, config);
        List<Model> models = new ArrayList<>();
        for (int i = 0; i < alignments.size(); i++) {
            alignments.set(i, viralProteinService.setViralProteinAttributes(alignments.get(i), config));
        }
        models.addAll(modelGenerationService.alignmentToModels(alignments.get(0), config));
        assertTrue(String.format("Expected at least 1 model, got %s", models.size()), 1 >= models.size());
        Model model = models.get(0);
        assertTrue(String.format("Expected models %s to have at least 2 exons, got %s", model, model.getExons().size()),
                2 <= model.getExons().size());
        model.getExons().remove(1);
        int maxIntronSize = 2500;
        int minMissingAASize = 10;
        List<Exon> missingExons = determineMissingExons.findMissingExons(model, maxIntronSize, minMissingAASize);
        assertEquals(1, missingExons.size());
    }

    @Test
    public void performPairWiseAlignment () {

        Range NTRange = Range.of(579, 2199);
        Range AARange = Range.of(190, 231);
        NucleotideSequence NTSequence = new NucleotideSequenceBuilder(
                "TGATCCAAAATGGAAGATTTTGTGCGACAATGCTTCAATCCAATGATTGTCGAGCTTGCGGAAAAGGCAATGAAAGAATATGGGGAAGATCCGAAAATCGAAACGAACAAATTTGCCGCAATAT"
                        + "GCACACACTTAGAGGTCTGTTTCATGTATTCGGATTTCCACTTTATTGATGAACGGGGCGAATCAATAATTGTAGAATCTGGCGATCCAAATGCATTATTGAAACACCGATTTGAGATAATTGAAGGGAGAGACCGAA"
                        + "CGATGGCCTGGACAGTGGTGAATAGTATCTGCAACACCACAGGAGTCGAGAAACCTAAATTTCTCCCAGATTTGTATGACTACAAAGAGAATCGATTCATTGAAATTGGAGTAACACGGAGGGAAGTTCATATATAC"
                        + "TATCTAGAAAAGGCCAACAAGATAAAATCAGAGAAGACACACATTCACATATTCTCATTCACTGGAGAGGAAATGGCCACCAAAGCGGACTACACTCTTGACGAAGAGAGTAGGGCAAGAATCAAAACCAGGCTGTTC"
                        + "ACTATAAGGCAGGAAATGGCCAGTAGGGGTCTATGGGATTCCTTTCGTCAGTCCGAGAGAGGCGAAGAGACAGTTGAAGAAAGATTTGAAATCACAGGAACCATGCGCAGGCTTGCCGACCAAAGTCTCCCACCGAACT"
                        + "TCTCCAGCCTTGAAAACTTTAGAGCCTATGTGGATGGATTCGAACCGAACGGCTGCATTGAGGGCAAGCTTTCTCAAATGTCAAAAGAAGTGAACGCCCGAATTGAGCCATTTCTGAAGACAACACCACGCCCTCTCA"
                        + "AACTACCTGACGGGCCTCCCTGCTCTCAACGGTCGAAGTTCCTGCTGATGGATGCCCTTAAATTAAGCATCGAAGACCCGAGTCATGAGGGGGAGGGTATACCGCTATATGATGCAATCAAATGCATGAAGACATTTTT"
                        + "CGGCTGGAAAGAGCCCAACATTGTAAAACCACATGAAAAGGGCATAAACCCCAATTACCTCCTGGCTTGGAAGCAAGTGCTGGCAGAACTCCAAGATATTGAAAATGAGGAGAAAATCCCAAAAACAAAGAACATGAAGAA"
                        + "AACGAGCCAGTTGAAGTGGGCACTTGGTGAGAATATGGCACCGGAGAAGGTAGACTTTGAGGATTGCAAGGATGTTAGCGATCTGAGACAGTATGACAGTGATGAACCAGAGTCTAGATCGCTAGCAAGCTGGATCCAGAGT"
                        + "GAATTCAACAAGGCATGTGAATTGACAGATTCAAGTTGGATTGAGCTTGATGAAATAGGGGAAGACATTGCTCCAATTGAGCACATTGCGAGTATGAGAAGAAACTACTTCACAGCGGAAGTATCCCATTGCAGGGCTACTGAA"
                        + "TACATAATGAAAGGAGTGTACATAAACACAGCCTTGTTGAATGCATCCTGTGCAGCCATGGATGACTTCCAACTGATTCCAATGATAAGCAAATGCAGGACCAAAGAAGGGAGGCGGAAGACTAATCTGTATGGATTCATTATA"
                        + "AAAGGAAGATCCCATTTGAGAAATGACACCGATGTAGTAAACTTTGTGAGCATGGAATTCTCTCTTACTGACCCGAGGCTGGAGCCACACAAGTGGGAAAAGTACTGTGTTCTCGAGATAGGAGACATGCTCCTACGGACTGC"
                        + "AATAGGCCAAGTGTCAAGGCCCATGTTCCTGTATGTGAGAACCAATGGGACTTCCAAGATCAAGATGAAGTGGGGCATGGAAATGAGGCGATGCCTTCTTCAATCCCTTCAACAAATTGAGAGCATGATTGAAGCCGAGTCTTC"
                        + "TGTCAAAGAGAAGGACATGACCAAAGAATTCTTTGAAAACAAATCAGAAACATGGCCAATTGGAGAGTCACCCAAAGGGGTGGAGGAAGGCTCCATTGGGAAGGTGTGCAGAACCTTACTGGCAAAATCTGTATTCAACAGCCTATA"
                        + "TGCATCTCCACAACTCGAGGGATTTTCAGCTGAATCAAGAAAGTTGCTTCTCATTGTCCAGGCACTTAGGGACAACCTGGAACCTGGGACCTTCGATCTTGGGGGGCTATATGAAGCAATTGAGGAGTGCCTGATTAATGATCCCTGGG"
                        + "TTTTGCTTAATGCGTCTTGGTTCAACTCCTTCCTCACACATGCACTGAAATAGTTGTGGCAATGCTACTATTTGCTATCCATACTGTCCAAAA")
                .build();
        ProteinSequence AASequence = new ProteinSequenceBuilder(
                "MEDFVRQCFNPMIVELAEKTMKEYGEDLKIETNKFAAICTHLEVCFMYSDFHFINEQGESIIVELGDPNALLKHRFEIIEGRDRTMAWTVVNSICNTTGAEKPKFLPDLYDYKENRFIEIGVTRREVHIYYLEKANKI"
                        + "KSEKTHIHIFSFTGEEMATKADYTLDEESRARIKTRLFTIRQEMASRGLWDSFVSPREEKRQLKKGLKSQEQCASLPTKVSRRTSPALKILEPM")
                .build();
        Optional<Exon> exon = determineMissingExons.performJillionPairWiseAlignment(NTRange, AARange, NTSequence,
                                                                                    AASequence, Direction.FORWARD);
        assertTrue(exon.isPresent());
        assertEquals(exon.get().getAlignmentFragment().getProteinSeqRange(), AARange);
    }

    @Test
    public void testJillionPairwiseAlignment () {

        ProteinSequence querySequence = new ProteinSequenceBuilder("MEDFVRQCFNPMIVELAEKTMKEYGEDLKIETNKFAAICTHLEVCFMYSDFHFI").build();
        ProteinSequence subjectSequence = new ProteinSequenceBuilder("MEDFVRQCFNPMIVELAEKTMKEYGEDLKIETNKFAAICTHLEVCFMYSDFHFINEQGESIIVELGDPNALLKHRFEIIEGRDRTMAWTVVNSICNTTGAEKPKF").build();
        AminoAcidSubstitutionMatrix blosom50 = BlosumMatrices.blosum50();
        ProteinPairwiseSequenceAlignment actual = PairwiseAlignmentBuilder
                .createProteinAlignmentBuilder(querySequence, subjectSequence, blosom50).gapPenalty(-8, -8)
                .build();
        DirectedRange expected;
        expected = DirectedRange.create(( Range.of(0, 53) ), Direction.FORWARD);
        DirectedRange queryRange = actual.getQueryRange();
        assertEquals(expected, queryRange);
    }
}
