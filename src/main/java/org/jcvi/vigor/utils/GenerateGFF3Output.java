package org.jcvi.vigor.utils;

import org.jcvi.jillion.core.Direction;
import org.jcvi.jillion.core.Range;
import org.jcvi.vigor.component.*;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;

@Service
public class GenerateGFF3Output {

    private static Range.CoordinateSystem oneBased = Range.CoordinateSystem.RESIDUE_BASED;

    public void generateOutputFile ( GenerateVigorOutput.Outfiles outfiles, List<Model> models ) throws IOException {

        printGFF3Features(outfiles.get(GenerateVigorOutput.Outfile.GFF3), models);
    }

    public void printGFF3Features ( BufferedWriter bw, List<Model> geneModels ) throws IOException {

        for (Model geneModel : geneModels) {
            List<NoteType> notes = geneModel.getNotes();
            int i = 1;
            VirusGenome virusGenome = geneModel.getAlignment().getVirusGenome();
            long seqlength = virusGenome.getSequence().getLength();
            String geneomeSeqID = virusGenome.getId();
            List<Exon> exons = geneModel.getExons();
            String geneName = geneModel.getAlignment().getViralProtein().getGeneSymbol();
            String CDSStart =Long.toString(VigorFunctionalUtils.getDirectionBasedCoordinate
                    (exons.get(0).getRange().getBegin(oneBased),seqlength,geneModel.getDirection()));
            String CDSEnd = Long.toString(VigorFunctionalUtils.getDirectionBasedCoordinate
                    (exons.get(exons.size() - 1).getRange().getEnd(oneBased),seqlength,geneModel.getDirection()));
            String mRnaID = geneModel.getGeneID() + "." + i;
            bw.write(geneomeSeqID + "\t" + "vigor" + "\t");
            //gene
            if (geneModel.isPseudogene()) {
                bw.write("pseudogene" + "\t");
            } else {
                bw.write("gene" + "\t");
            }
            bw.write(CDSStart + "\t" + CDSEnd + "\t");
            bw.write("." + "\t");
            if (geneModel.getDirection().equals(Direction.FORWARD)) {
                bw.write("+" + "\t");
            } else bw.write("-" + "\t");
            bw.write(exons.get(0).getFrame().getFrame() - 1 + "\t");
            bw.write("ID=" + geneModel.getGeneID() + ";" + "Name=" + geneName + ";");
            if (geneModel.isPartial3p() || geneModel.isPartial5p()) {
                bw.write("Partial" + ",");
            }
            if (notes.contains(NoteType.Gene)) bw.write(String.format("Note=%s;", NoteType.Gene));
            bw.write("\n");
            //mRNA
            bw.write(geneomeSeqID + "\t" + "vigor" + "\t");
            bw.write("mRNA" + "\t");
            bw.write(CDSStart + "\t" + CDSEnd + "\t");
            bw.write("." + "\t");
            if (geneModel.getDirection().equals(Direction.FORWARD)) {
                bw.write("+" + "\t");
            } else bw.write("-" + "\t");
            bw.write(exons.get(0).getFrame().getFrame() - 1 + "\t");
            bw.write("ID=" + mRnaID + ";" + "Parent=" + geneModel.getGeneID() + ";");
            if (geneModel.isPartial3p() || geneModel.isPartial5p()) {
                bw.write("Partial;");
            }
            bw.write("\n");
            //remark
            /*bw.write(geneomeSeqID+"\t"+"vigor"+"\t");
            bw.write("remark"+"\t");
            bw.write(".\t.\t.\t.\t.\t");
            bw.write("Parent="+mRnaID+";");
            bw.write("Note={};");
            bw.write("\n");*/
            //exon
            IDGenerator idGenerator = new IDGenerator(mRnaID);
            for (int j = 0; j < exons.size(); j++) {
                Exon exon = exons.get(j);
                bw.write(geneomeSeqID + "\t" + "vigor" + "\t");
                bw.write("exon" + "\t");
                String exonBegin =Long.toString(VigorFunctionalUtils.getDirectionBasedCoordinate(exon.getRange().getBegin(oneBased),seqlength,geneModel.getDirection()));
                String exonEnd =Long.toString(VigorFunctionalUtils.getDirectionBasedCoordinate(exon.getRange().getEnd(oneBased),seqlength,geneModel.getDirection()));
                bw.write(exonBegin + "\t" + exonEnd + "\t");
                bw.write("." + "\t");
                if (geneModel.getDirection().equals(Direction.FORWARD)) {
                    bw.write("+" + "\t");
                } else bw.write("-" + "\t");
                bw.write(exon.getFrame().getFrame() - 1 + "\t");
                bw.write(String.format("ID=%s;Parent=%s;", idGenerator.next(), mRnaID));
                if (( j == 0 && geneModel.isPartial5p() ) || ( j == exons.size() - 1 && geneModel.isPartial3p() )) {
                    bw.write("Partial" + ";");
                }
                bw.write("\n");
            }
            //CDS
            bw.write(geneomeSeqID + "\t" + "vigor" + "\t");
            bw.write("CDS" + "\t");
            bw.write(CDSStart + "\t" + CDSEnd + "\t");
            bw.write("." + "\t");
            if (geneModel.getDirection().equals(Direction.FORWARD)) {
                bw.write("+" + "\t");
            } else bw.write("-" + "\t");
            bw.write(exons.get(0).getFrame().getFrame() - 1 + "\t");
            bw.write(String.format("ID=%s;Parent=%s;", idGenerator.next(), geneModel.getGeneID()));
            if (geneModel.isPartial3p() || geneModel.isPartial5p()) {
                bw.write("Partial" + ";");
            }
            bw.write("\n");
            //insertion
            if (geneModel.getInsertRNAEditingRange() != null) {
                bw.write(geneomeSeqID + "\t" + "vigor" + "\t");
                bw.write("insertion" + "\t");
                String insertBegin= Long.toString(VigorFunctionalUtils.getDirectionBasedCoordinate(geneModel.getInsertRNAEditingRange().getBegin(oneBased),seqlength,geneModel.getDirection()));
                String insertEnd=Long.toString(VigorFunctionalUtils.getDirectionBasedCoordinate(geneModel.getInsertRNAEditingRange().getEnd(oneBased),seqlength,geneModel.getDirection()));
                bw.write(insertBegin + "\t" + insertEnd + "\t");
                bw.write("." + "\t");
                if (geneModel.getDirection().equals(Direction.FORWARD)) {
                    bw.write("+" + "\t");
                } else bw.write("-" + "\t");
                bw.write("." + "\t");
                bw.write(String.format("ID=%s;Parent=%s;", idGenerator.next(), mRnaID));
                if (notes.contains(NoteType.RNA_Editing)) bw.write(String.format("Note=%s;", NoteType.RNA_Editing));
                bw.write("\n");
            }
            //Stop_codon_read_through
            if (geneModel.getReplaceStopCodonRange() != null) {
                bw.write(geneomeSeqID + "\t" + "vigor" + "\t");
                bw.write("stop_codon_read_through" + "\t");
                long replaceStopBegin= VigorFunctionalUtils.getDirectionBasedCoordinate(geneModel.getReplaceStopCodonRange().getBegin(oneBased),seqlength,geneModel.getDirection());
                long replaceStopEnd=VigorFunctionalUtils.getDirectionBasedCoordinate(geneModel.getReplaceStopCodonRange().getEnd(oneBased),seqlength,geneModel.getDirection());
                bw.write(Long.toString(replaceStopBegin) + "\t" + Long.toString(replaceStopEnd) + "\t");
                bw.write("." + "\t");
                if (geneModel.getDirection().equals(Direction.FORWARD)) {
                    bw.write("+" + "\t");
                } else bw.write("-" + "\t");
                bw.write("." + "\t");
                bw.write(String.format("ID=%s;Parent=%s;", idGenerator.next(), mRnaID));
                if (notes.contains(NoteType.StopCodonReadThrough))
                    bw.write(String.format("Note=%s;", ( NoteType.StopCodonReadThrough )));
                bw.write("\n");
            }
            //minus_1_translationally_frameshifted
            if (geneModel.getAlignment().getViralProtein().getGeneAttributes().getRibosomal_slippage().isHas_ribosomal_slippage() && geneModel.getRibosomalSlippageRange() != null) {
                int frameshift = geneModel.getAlignment().getViralProtein().getGeneAttributes().getRibosomal_slippage().getSlippage_frameshift();
                long slippageBegin = VigorFunctionalUtils.getDirectionBasedCoordinate(geneModel.getRibosomalSlippageRange().getBegin(oneBased),seqlength,geneModel.getDirection());
                long slippageEnd = VigorFunctionalUtils.getDirectionBasedCoordinate(geneModel.getRibosomalSlippageRange().getEnd(oneBased),seqlength,geneModel.getDirection());
                if (frameshift == -1) {
                    bw.write(geneomeSeqID + "\t" + "vigor" + "\t");
                    bw.write("mRNA_with_minus_1_frameshift" + "\t");
                    bw.write(Long.toString(slippageBegin)+ "\t" + Long.toString(slippageEnd) + "\t");
                    bw.write("." + "\t");
                    if (geneModel.getDirection().equals(Direction.FORWARD)) {
                        bw.write("+" + "\t");
                    } else bw.write("-" + "\t");
                    bw.write("." + "\t");
                    bw.write(String.format("ID=%s;Parent=%s;", idGenerator.next(), mRnaID));
                    //bw.write("Note={};");
                    bw.write("\n");
                }
                //plus_1_translationally_frameshifted
                if (frameshift == 1) {
                    bw.write(geneomeSeqID + "\t" + "vigor" + "\t");
                    bw.write("mRNA_with_plus_1_frameshift" + "\t");
                    bw.write(Long.toString(slippageBegin)+ "\t" + Long.toString(slippageEnd) + "\t");
                    bw.write("." + "\t");
                    if (geneModel.getDirection().equals(Direction.FORWARD)) {
                        bw.write("+" + "\t");
                    } else bw.write("-" + "\t");
                    bw.write("." + "\t");
                    bw.write(String.format("ID=%s;Parent=%s;", idGenerator.next(), mRnaID));
                    // bw.write("Note={};");
                    bw.write("\n");
                }
            }
        }
    }
}
