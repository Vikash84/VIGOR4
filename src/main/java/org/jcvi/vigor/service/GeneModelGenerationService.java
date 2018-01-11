package org.jcvi.vigor.service;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.jcvi.vigor.component.Model;
import org.jcvi.vigor.forms.VigorForm;
import org.jcvi.vigor.utils.FormatVigorOutput;

@Service
public class GeneModelGenerationService {
	@Autowired
	private DetermineMissingExons determineMissingExons;
	@Autowired
	private DetermineStart determineStart;
	@Autowired
	private AdjustViralTricks adjustViralTricks;
	@Autowired
	private AdjustUneditedExonBoundaries adjustUneditedExonBounds;
	@Autowired
	private DetermineStop determineStop;
	@Autowired
	private CheckCoverage checkCoverage;
	@Autowired
	private EvaluateScores evaluateScores;
	boolean isDebug = true;
	List<Model> partialGeneModels = new ArrayList<Model>();
	List<Model> pseudoGenes = new ArrayList<Model>();

	public void generateGeneModel(List<Model> models, VigorForm form) {		
		isDebug = form.isDebug();
		List<Model> processedModels = determineGeneFeatures(models,form);
		processedModels.stream().forEach(model-> checkCoverage.evaluate(model,form));
		processedModels.stream().forEach(model-> evaluateScores.evaluate(model, form));
		
		processedModels.sort(new Comparator<Model>(){
			@Override
			public int compare(Model m1, Model m2){
				return Double.compare(m1.getScores().get("totalScore"), m2.getScores().get("totalScore"));
		}
		});
		processedModels.forEach(System.out::println);

	}

	public void generateOutputFiles(List<Model> models){

	    models.forEach(System.out::println);
    }

	public List<Model> determineGeneFeatures(List<Model> models,VigorForm form){
			
	    List<Model> modelsWithMissingExonsDetermined=new ArrayList<Model>();
	    List<Model> modelsAfterDeterminingStart =new ArrayList<Model>();
	 	List<Model> modelsAfterDeterminingViralTricks=new ArrayList<Model>();
	 	List<Model> modelsAfterAdjustingBounds = new ArrayList<Model>();
	 	List<Model> modelsAfterDeterminingStop = new ArrayList<Model>();
	     
	  				
		/* Determine Start */
		models.stream().forEach(model -> { 
			if(!model.isPartial5p()){
			List<Model> outputModels = determineStart.determine(model, form);
			outputModels.stream().forEach(model1->{
				if((model.isPartial5p())) {
					partialGeneModels.add(model);
				}else if (model.isPseudogene()){
					pseudoGenes.add(model1);
				}else{
					modelsAfterDeterminingStart.add(model1);
				}
				
			});
			}else{
				partialGeneModels.add(model);
			}
				});
		if(isDebug)FormatVigorOutput.printModelsWithStart(modelsAfterDeterminingStart, "After Determining Start");
		
		/*Adjust RNAEditing, Ribosomal Slippage and find StopCodonReadThrough*/
		modelsAfterDeterminingStart.stream().forEach(x->{
			List<Model> outputModels = adjustViralTricks.determine(x, form);
			modelsAfterDeterminingViralTricks.addAll(outputModels);
		});	
		
		/*Adjust unedited Exon boundaries*/
		modelsAfterDeterminingViralTricks.stream().forEach(model->{
			List<Model> outputModels = adjustUneditedExonBounds.determine(model, form);
			for(Model adjustedModel : outputModels){
				int exonsCount = adjustedModel.getExons().size();
				Model missingExonsDeterminedModel = determineMissingExons.determine(adjustedModel, form).get(0);
				int afterExonsCount = missingExonsDeterminedModel.getExons().size();
				if(afterExonsCount>exonsCount){
			 List<Model> revisitedModels = adjustUneditedExonBounds.determine(missingExonsDeterminedModel, form);
			 modelsWithMissingExonsDetermined.addAll(revisitedModels);
			 }else{
			 modelsWithMissingExonsDetermined.add(adjustedModel);
				}
			}
			modelsAfterAdjustingBounds.addAll(outputModels);
			
		});
		
		if(isDebug)FormatVigorOutput.printModels(modelsWithMissingExonsDetermined,"After determining missing exons");
		
		
		/* Determine Stop */
		modelsWithMissingExonsDetermined.stream().forEach(x -> { 
			if(!x.isPartial3p()){
			List<Model> outputModels = determineStop.determine(x, form);
			outputModels.stream().forEach(model->{
				if(model.isPseudogene()) {
					pseudoGenes.add(model);
				}else if(model.isPartial3p()){
					partialGeneModels.add(model);
				}else{
					modelsAfterDeterminingStop.add(model);
				}				
			});
			}else{
				partialGeneModels.add(x);
			}
				});
		if(isDebug)FormatVigorOutput.printModels(modelsAfterDeterminingStop,"Models after determining stop");
		
		return modelsAfterDeterminingStop;
	}
	
	
	
	}

