package edu.arizona.verbs.experiments;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import ros.pkg.verb_learning.srv.PerformVerb.Response;
import au.com.bytecode.opencsv.CSVWriter;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import edu.arizona.cs.learn.algorithm.markov.FSMRecognizer;
import edu.arizona.cs.learn.timeseries.model.Interval;
import edu.arizona.simulator.ww2d.external.WW2DEnvironment;
import edu.arizona.verbs.environments.Simulators;
import edu.arizona.verbs.experiments.evaluation.EvaluationResults;
import edu.arizona.verbs.experiments.evaluation.Evaluator;
import edu.arizona.verbs.experiments.evaluation.Label;
import edu.arizona.verbs.experiments.evaluation.Labeler;
import edu.arizona.verbs.mdp.StateConverter;
import edu.arizona.verbs.shared.Environment;
import edu.arizona.verbs.shared.OOMDPState;
import edu.arizona.verbs.verb.Verb;
import edu.arizona.verbs.verb.Verbs;
import edu.arizona.verbs.verb.vfsm.AtomicVerb;
import edu.arizona.verbs.verb.vfsm.learner.SignatureLearner;

public class Experimenter {
	public static String teacherFolder = System.getProperty("user.dir") + "/data/teacher/";
	
	public static List<Scenario> loadScenarios(String environment, String verb) throws FileNotFoundException {
		FileInputStream fis = new FileInputStream(teacherFolder + environment + "/" + verb + ".yaml");
		
		Constructor constructor = new Constructor(Scenario.class);
		Yaml yaml = new Yaml(constructor);
		
		List<Scenario> scenarios = Lists.newArrayList();
		
		for (Object obj : yaml.loadAll(fis)) {
			Scenario scenario = (Scenario) obj;
//			System.out.println("\nSCENARIO:");
//			
//			System.out.println(scenario.start);
//			System.out.println(scenario.actions);
//			
//			for (OOMDPObjectState objectState : scenario.start) {
//				System.out.println(objectState.getAttributeNames());
//				System.out.println(objectState.getAttributes());
//			}
			scenarios.add(scenario);
		}
		
		return scenarios;
	}
	
	public static List<OOMDPState> demonstrate(Scenario scenario, Environment environment) {
		List<OOMDPState> teacherTrace = Lists.newArrayList();
		
		OOMDPState startState = environment.initializeEnvironment(scenario.start);
		teacherTrace.add(startState);
		for (String action : scenario.actions) {
			OOMDPState nextState = environment.performAction(action);
			teacherTrace.add(nextState);
		}
		
		return teacherTrace;
	}

	public static class RecognitionTest {
		public List<OOMDPState> trace = null;
		public List<String> verbs = Lists.newArrayList();
	}
	
	public static List<RecognitionTest> makeRecognitionCorpus(String testVerb, String env) throws Exception {
		Set<String> allVerbs = Sets.newHashSet("go", "go_via", "intercept", "avoid", "follow"); // could add "meet" as distractors 
		Set<String> otherVerbs = Sets.newHashSet(allVerbs);
		otherVerbs.remove(testVerb);
		otherVerbs.remove("go"); // None of the other verbs match the arity of "go", so "go" is excluded 
		
		List<RecognitionTest> corpus = Lists.newArrayList();
		
		Environment environment = Simulators.valueOf(env).create();
		
		for (String verb : otherVerbs) {
			List<Scenario> scenarios = loadScenarios(env, verb);
			
			for (Scenario scenario : scenarios) {
				RecognitionTest test = new RecognitionTest();
				test.trace = demonstrate(scenario, environment);
				for (String verb2 : allVerbs) {
					if (Label.Success == Labeler.valueOf(verb2).label(test.trace)) {
						test.verbs.add(verb2);
					}
				}
				
//				if (testVerb.equals("avoid") && test.verbs.contains("avoid")) {
//					System.out.println(test.verbs);
//				}
				
				corpus.add(test);
			}
		}
		
		return corpus;
	}
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		// These will become CLI parameters
		String env = "ww2d";
		

		String verb = "go";
		ArrayList<String> arguments = Lists.newArrayList("agent", "place");

//		String verb = "go_via";
//		ArrayList<String> arguments = Lists.newArrayList("agent", "waypoint", "place");
		
//		String verb = "intercept";
//		ArrayList<String> arguments = Lists.newArrayList("agent", "enemy", "place");
		
//		String verb = "follow";
//		ArrayList<String> arguments = Lists.newArrayList("agent", "enemy", "place");
//		
//		String verb = "avoid";
//		ArrayList<String> arguments = Lists.newArrayList("agent", "enemy", "place");
		


		String model = "signature";
//		String model = "naive";
//		String model = "corepath";
//		String model = "ml";
		int numTrajectories = 4;
		
		// Load the evironment
//		WW2DEnvironment.visualize = true;
		Environment environment = Simulators.valueOf(env).create();

		// Load the scenario file
		List<Scenario> scenarios = loadScenarios(env, verb);

		// Pre-cache the teacher demonstrations
		List<List<OOMDPState>> demonstrations = Lists.newArrayList();
		for (Scenario scenario : scenarios) {
			demonstrations.add(demonstrate(scenario, environment));
		}
		
		// Make the recognition corpus (partially redundant with the last step, should probably merge)
		List<RecognitionTest> corpus = makeRecognitionCorpus(verb, env);
		
		File dataFolder = new File(System.getProperty("user.dir") + "/data/csv/" + verb + "_" + model);
		if (!dataFolder.isDirectory()) {
			dataFolder.mkdir();
		}
		
		// Create the CSV file
		String csvString = String.valueOf(System.currentTimeMillis());
		CSVWriter writer = new CSVWriter(
			new FileWriter(dataFolder.getAbsolutePath() + "/" + csvString + ".csv"));
		
		CSVWriter recCSV = new CSVWriter(
				new FileWriter(dataFolder.getAbsolutePath() + "/" + "r2" + csvString + ".csv"));
		
		for (int i = 0; i < numTrajectories; i++) {
			
			// Regenerate the verb each trajectory
			Verb v = Verbs.valueOf(model).create(verb, arguments);
			
			List<Integer> randomized = Lists.newArrayList();
			for (int n = 0; n < scenarios.size(); n++) {
				randomized.add(n);
			}
			Collections.shuffle(randomized);
			
			int current = 0;
//			boolean[] correct = new boolean[randomized.size()];		
			String[] labels = new String[randomized.size()];
			String[] times = new String[randomized.size()];
			String[] precision = new String[randomized.size()];
			String[] recall = new String[randomized.size()];
			String[] f = new String[randomized.size()];
			
			for (int index : randomized) {
				
				//////////////////////////////////////////////////////
				// Step 0: Optional recognition test
				if (model.equals("signature") || model.equals("naive") || model.equals("corepath")) {
					if (current != 0) {
						System.out.println("\n$$$$$$$$ BEGIN RECOGNITION TEST");
						
						// 1 held-out instance of the current verb
						RecognitionTest heldOut = new RecognitionTest();
						heldOut.trace = demonstrations.get(index);
						heldOut.verbs.add(verb);
						
						List<RecognitionTest> subcorpus = Lists.newArrayList(heldOut);
						List<RecognitionTest> randomCorpus = new ArrayList<RecognitionTest>(corpus);
						Random r = new Random();
						for (int j = 0; j < 19; j++) {
							subcorpus.add(randomCorpus.remove(r.nextInt(randomCorpus.size())));
						}
						
						boolean[] proposed = new boolean[subcorpus.size()];
						boolean[] actual = new boolean[subcorpus.size()];
						
						for (int j = 0; j < subcorpus.size(); j++) {
							RecognitionTest test = subcorpus.get(j);
							Map<String,String> nameMap = Maps.newHashMap(); // Later will need to do real remap
							List<Interval> intervals = StateConverter.traceToIntervals(test.trace, nameMap);
							
							AtomicVerb trueVerb = (AtomicVerb) v;
							FSMRecognizer recognizer = trueVerb.getPosLearner().getRecognizer();
							
							if (trueVerb.getNegLearner().isReady()) {
								FSMRecognizer negRecognizer = trueVerb.getNegLearner().getRecognizer();
								boolean posAccept = recognizer.test(intervals);
								boolean negAccept = negRecognizer.test(intervals);
								proposed[j] = posAccept && !negAccept;
								
								System.out.println(posAccept + " " + negAccept + " " + test.verbs);
								"a".length();
							} else {
								proposed[j] = recognizer.test(intervals);
							}
							// TODO: Test the effect of negative on avoid
							actual[j] = test.verbs.contains(v.getLexicalForm());
						}
						
						EvaluationResults results = Evaluator.evaluate(proposed, actual);
						results.printResults();
						
						precision[current] = String.valueOf(results.precision);
						recall[current] = String.valueOf(results.recall);
						f[current] = String.valueOf(results.f());
						
						System.out.println("$$$$$$$$$$ END RECOGNITION TEST\n");
					} else {
						// Can't run the test on the first cycle
						precision[current] = String.valueOf(0.0);
						recall[current] = String.valueOf(0.0);
						f[current] = String.valueOf(0.0);
					}
				}
				
				//////////////////////////////////////////////////////
				// Step 1: Agent performs the verb
				OOMDPState startState = environment.initializeEnvironment(scenarios.get(index).start);
				Response response = v.perform(environment, startState, 30);
				
				List<OOMDPState> studentTrace = StateConverter.msgArrayListToStates(response.trace);
				
				//////////////////////////////////////////////////////
				// Step 2: Teacher labels performance
				Label label = Labeler.valueOf(verb).label(studentTrace);
				System.out.println(label);
				switch (label) {
				case Success:
					v.addPositiveInstance(studentTrace);
//					correct[current] = true;
					break;
				case Violation:
					v.addNegativeInstance(studentTrace);
					break;
				}
				
				labels[current] = label.toString();
				times[current] = String.valueOf(response.planning_time);
				
				//////////////////////////////////////////////////////
				// Step 3: Teacher demonstrates
//				List<OOMDPState> demonstration = demonstrate(scenario, environment);
				v.addPositiveInstance(demonstrations.get(index));
				
				current++;
			}
			
			// Write the CSV file
			writer.writeNext(labels);
			writer.writeNext(times);
			writer.flush();
			
			// Write the recognition CSV file
			recCSV.writeNext(precision);
			recCSV.writeNext(recall);
			recCSV.writeNext(f);
			recCSV.flush();
		}
		
		// Close the CSV file
		writer.close();
		recCSV.close();
	}
}
