package org.jcvi.vigor.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jcvi.jillion.core.Range;
import org.jcvi.jillion.core.residue.Frame;
import org.jcvi.jillion.core.residue.aa.IupacTranslationTables;
import org.jcvi.jillion.core.residue.nt.NucleotideSequence;
import org.jcvi.jillion.core.residue.nt.Triplet;
import org.springframework.stereotype.Service;
import org.jcvi.vigor.component.Exon;
import org.jcvi.vigor.component.Model;
import org.jcvi.vigor.forms.VigorForm;
import org.jcvi.vigor.utils.*;

@Service
public class DetermineStart implements DetermineGeneFeatures {

	private static final Logger LOGGER = LogManager
			.getLogger(DetermineStart.class);

	@Override
	public List<Model> determine(Model model, VigorForm form) {
		List<Model> models = null;
		try {
			List<Triplet> startCodons = LoadStartCodons(model.getAlignment()
					.getViralProtein().getGeneAttributes()
					.getStartTranslationException().getAlternateStartCodons(),
					form.getVigorParametersList());
			String startCodonWindowParam = form.getVigorParametersList().get(
					"start_codon_search_window");
			
			models = findStart(startCodons, model, startCodonWindowParam);

		}
		catch(CloneNotSupportedException e){
			LOGGER.error(e.getMessage(),e);
			System.exit(0);
		}

		catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			System.exit(0);
		}
        System.out.println("Models after determining start");
		models.forEach(System.out::println);
		return models;
	}

	/**
	 * 
	 * @param alternateStartCodons
	 *            defined in defline of reference protein
	 * @param vigorParameters
	 *            : Default start codons defined in vigor.ini file
	 * @return List of all the possible start codons
	 */
	public List<Triplet> LoadStartCodons(List<String> alternateStartCodons,
			Map<String, String> vigorParameters) {
		String startCodonsParam;
		List<String> startCodonStrings = new ArrayList<String>();
		if (vigorParameters.containsKey("StartCodons")) {
			startCodonsParam = vigorParameters.get("StartCodons");
			startCodonStrings = Arrays.asList(StringUtils.normalizeSpace(
					startCodonsParam).split(","));
		} else {

			startCodonStrings.add("ATG");
		}
		if (alternateStartCodons != null) {
			startCodonStrings.addAll(alternateStartCodons);
		}
		List<Triplet> startCodons = new ArrayList<Triplet>();
		for (String startCodonString : startCodonStrings) {
			if (startCodonString.length() == 3) {
				Triplet triplet = Triplet.create(startCodonString.charAt(0),
						startCodonString.charAt(1), startCodonString.charAt(2));
				startCodons.add(triplet);
			}

		}
		return startCodons;
	}

	public List<Model> findStart(List<Triplet> startCodons, Model model,
		String startCodonWindowParam) throws CloneNotSupportedException {
		List<Model> newModels = new ArrayList<Model>();
		long start;
		long end;
		Range startSearchRange;
		int windowSize = 50;
		boolean isSequenceMissing = false;
		if (startCodonWindowParam != null) {
			if (VigorUtils.is_Integer(startCodonWindowParam)) {
				windowSize = Integer.parseInt(startCodonWindowParam);
			}
		}
		Exon firstExon = model.getExons().get(0);
		Frame firstExonFrame = VigorFunctionalUtils.getSequenceFrame(firstExon.getRange().getBegin());
		long expectedStart = firstExon.getRange().getBegin()
					- ((firstExon.getAlignmentFragment().getProteinSeqRange()
							.getBegin()) * 3);
			start = expectedStart - windowSize;
		if (start < 0) {
			isSequenceMissing = true;
			start = 0;
		}
		end = start+windowSize;
		if(end>firstExon.getRange().getEnd()){
		    end = firstExon.getRange().getEnd();
        }
		startSearchRange = Range.of(start, end);
		//find any internal stops
        Map<Frame,List<Long>> internalStops = model.getAlignment().getVirusGenome().getInternalStops();
        List<Long> stopsInFrame=new ArrayList<Long>();

        if(internalStops!=null) {
            for (Frame frame : internalStops.keySet()) {
                if (frame.equals(firstExonFrame)) {
                    List<Long> tempList = internalStops.get(firstExonFrame);
                    for(Long stop : tempList){
                        Range tempRange = Range.of(stop);
                        if(tempRange.isSubRangeOf(startSearchRange)){
                            stopsInFrame.add(stop);
                        }
                    }
                }
            }
        }


		final long tempStart = start;
		NucleotideSequence NTSequence = model.getAlignment().getVirusGenome()
				.getSequence().toBuilder(startSearchRange).build();
		Map<Range, Double> rangeScoreMap = new HashMap<Range, Double>();
		for (Triplet triplet : startCodons) {
			Stream<Range> stream = NTSequence.findMatches(triplet.toString());
			List<Range> rangesInFrame = new ArrayList<Range>();
			List<Range> foundRanges = stream.map(
					range -> {
						range = Range.of(range.getBegin() + tempStart,
								range.getEnd() + tempStart);
						return range;
					}).collect(Collectors.toList());


			foundRanges.stream().forEach(x -> {
				if (VigorFunctionalUtils.getSequenceFrame(x.getBegin()).compareTo(firstExonFrame) == 0) {
					rangesInFrame.add(x);
				}
			});
			for(Range range:rangesInFrame){
			    boolean isValid = true;
			    for(Long stop: stopsInFrame){
			        if(range.endsBefore(Range.of(stop,stop+2))){
			            isValid=false;
                    }
                }
                if(isValid) {
                    rangeScoreMap.put(range, VigorFunctionalUtils.generateScore(firstExon.getRange().getBegin(), range.getBegin()));
                }
			}
		}
		rangeScoreMap
				.entrySet()
				.stream()
				.sorted(Map.Entry.comparingByValue())
				.collect(
						Collectors.toMap(Map.Entry::getKey,
								Map.Entry::getValue, (e1, e2) -> e2,
								LinkedHashMap::new));
		if (!(rangeScoreMap.isEmpty())) {
			Set<Range> keys = rangeScoreMap.keySet();
			for (Range range : keys) {

				Model newModel = new Model();
				newModel = model.clone();
				Exon fExon = newModel.getExons().get(0);
				fExon.setRange(Range.of(range.getBegin(),fExon.getRange().getEnd()));
				if (model.getScores() != null) {
				   newModel.getScores().put("startCodonScore",
                           rangeScoreMap.get(range));
				   } else {
					Map<String, Double> scores = new HashMap<String, Double>();
					scores.put("startCodonScore", rangeScoreMap.get(range));
					newModel.setScores(scores);
				}
				newModels.add(newModel);
			}
		}else{
			System.out.println("Start not found");
		}
		if (rangeScoreMap.isEmpty() && isSequenceMissing) {
			Model newModel = new Model();
			newModel = model.clone();
			newModel.setPartial5p(true);
			newModels.add(newModel);
			System.out.println("Sequence is missin. No Start found. Partial gene "+newModel.getAlignment().getViralProtein().getProteinID());

		} else if (rangeScoreMap.isEmpty()) {
			Model newModel = new Model();
			newModel = model.clone();
			newModel.setPseudogene(true);
			newModels.add(newModel);	
			System.out.println("Pseudogene. No Start found. "+newModel.getAlignment().getViralProtein().getProteinID());
		}

		return newModels;
	}

 

	
	
}


