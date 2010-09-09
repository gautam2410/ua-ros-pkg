package edu.arizona.cs.learn.timeseries.dissertation;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.arizona.cs.learn.algorithm.alignment.model.Instance;
import edu.arizona.cs.learn.algorithm.alignment.model.WeightedObject;
import edu.arizona.cs.learn.algorithm.bpp.BPPFactory;
import edu.arizona.cs.learn.algorithm.heatmap.HeatmapImage;
import edu.arizona.cs.learn.timeseries.model.AllenRelation;
import edu.arizona.cs.learn.timeseries.model.Interval;
import edu.arizona.cs.learn.timeseries.model.Signature;
import edu.arizona.cs.learn.util.SequenceType;
import edu.arizona.cs.learn.util.Utils;

public class Heatmaps {
	public static void main(String[] args) {
		SignatureExample.init();
		AllenRelation.text = AllenRelation.partialText;

		edu.arizona.cs.learn.algorithm.render.Paint._rowHeight = 14;
		edu.arizona.cs.learn.algorithm.render.Paint._timeWidth = 7;
		edu.arizona.cs.learn.algorithm.render.Paint._fontSize = 12.0F;

		heatmap(SequenceType.allen, 2);
	}

	public static void ww3d() {
		Signature jumpOver = Signature
				.fromXML("data/cross-validation/k10/fold-0/allen/ww3d-jump-over.xml");

		int min = (int) Math.round(0.5D * jumpOver.trainingSize());
		System.out.println("min: " + min);
		jumpOver = jumpOver.prune(min);

		Map<Integer, List<Interval>> jumpOverMap = Utils.load(new File(
				"data/input/ww3d-jump-over.lisp"));
		Map<Integer, List<Interval>> jumpOnMap = Utils.load(new File(
				"data/input/ww3d-jump-on.lisp"));
		Map<Integer, List<Interval>> approachMap = Utils.load(new File(
				"data/input/ww3d-approach.lisp"));

		List<Interval> c1 = BPPFactory.compress(
				jumpOverMap.get(Integer.valueOf(1)), Interval.eff);
		List<Interval> c2 = BPPFactory.compress(
				jumpOnMap.get(Integer.valueOf(2)), Interval.eff);
		List<Interval> c3 = BPPFactory.compress(
				approachMap.get(Integer.valueOf(1)), Interval.eff);

		HeatmapImage.makeHeatmap("data/images/jump-over.png",
				jumpOver.signature(), min, c1, SequenceType.allen);
		HeatmapImage.makeHeatmap("data/images/jump-on.png",
				jumpOver.signature(), min, c2, SequenceType.allen);
		HeatmapImage.makeHeatmap("data/images/approach.png",
				jumpOver.signature(), min, c3, SequenceType.allen);
	}

	public static void ww2d() {
		Signature chase = Signature
				.fromXML("data/cross-validation/k10/fold-0/allen/ww2d-chase-prune.xml");

		int min = (int) Math.round(0.5D * chase.trainingSize());
		System.out.println("min: " + min);
		chase = chase.prune(min);

		Map<Integer, List<Interval>> chaseMap = Utils.load(new File(
				"data/input/ww2d-chase.lisp"));
		Map<Integer, List<Interval>> fightMap = Utils.load(new File(
				"data/input/ww2d-fight.lisp"));
		Map<Integer, List<Interval>> ballMap = Utils.load(new File(
				"data/input/ww2d-ball.lisp"));

		List<Interval> c1 = BPPFactory.compress(
				chaseMap.get(Integer.valueOf(1)), Interval.eff);
		List<Interval> c2 = BPPFactory.compress(
				fightMap.get(Integer.valueOf(2)), Interval.eff);
		List<Interval> c3 = BPPFactory.compress(
				ballMap.get(Integer.valueOf(1)), Interval.eff);

		HeatmapImage.makeHeatmap("data/images/chase.png", chase.signature(),
				min, c1, SequenceType.allen);
		HeatmapImage.makeHeatmap("data/images/fight.png", chase.signature(),
				min, c2, SequenceType.allen);
		HeatmapImage.makeHeatmap("data/images/ball.png", chase.signature(),
				min, c3, SequenceType.allen);
	}

	public static void heatmap(String prefix, SequenceType type, int k,
			int fold, String activity, double prunePct, String activity2, int id) {
		Signature s = Signature.fromXML("data/cross-validation/k" + k
				+ "/fold-" + fold + "/" + type + "/" + prefix + "-" + activity
				+ ".xml");

		int min = (int) Math.round(prunePct * s.trainingSize());
		System.out.println("min: " + min);
		s = s.prune(min);

		Map<Integer, List<Interval>> map = Utils.load(new File("data/input/"
				+ prefix + "-" + activity2 + ".lisp"));
		List<Interval> c1 = BPPFactory.compress(map.get(Integer.valueOf(id)),
				Interval.eff);

		HeatmapImage.makeHeatmap(
				"data/images/" + activity2 + "-" + id + ".png", s.signature(),
				min, c1, type);
	}

	public static void heatmap(SequenceType type, int min) {
		Map<String, List<Instance>> map = Utils.load("chpt1-",
				type);
		List<Instance> list = map.get("chpt1-approach");
		List<List<Interval>> episodes = new ArrayList<List<Interval>>();
		Signature s = new Signature("approaches");
		for (Instance i : list) {
			s.update(i.sequence());

			Map<String, Interval> intervalMap = new HashMap<String, Interval>();
			for (WeightedObject obj : i.sequence()) {
				for (Interval interval : obj.key().getIntervals()) {
					intervalMap.put(interval.toString(), interval);
				}
			}
			List<Interval> intervals = new ArrayList<Interval>(
					intervalMap.values());
			episodes.add(intervals);
		}

		if (min > 0) {
			s = s.prune(min);
		}
		String f = "/Users/wkerr/Desktop/heatmap-" + type + "-idx.png";
		for (int i = 1; i <= episodes.size(); i++)
			HeatmapImage.makeHeatmap(f.replaceAll("idx", i + ""),
					s.signature(), min, episodes.get(i - 1), type);
	}

	public static void relationalMaps() {
		rmap1();
		rmap2();
		rmap3();
	}

	public static void rmap1() {
		List<WeightedObject> signature = new ArrayList<WeightedObject>();
		signature.add(new WeightedObject(new AllenRelation("A","finishes-with", "D"), 1.0D));
		signature.add(new WeightedObject(new AllenRelation("A", "overlaps", "B"), 5.0D));
		signature.add(new WeightedObject(new AllenRelation("A", "meets", "C"),5.0D));
		signature.add(new WeightedObject(new AllenRelation("D", "overlaps", "B"), 1.0D));
		signature.add(new WeightedObject(new AllenRelation("D", "meets", "C"),1.0D));
		signature.add(new WeightedObject(new AllenRelation("B", "overlaps", "C"), 5.0D));

		List<Interval> episode = new ArrayList<Interval>();
		episode.add(Interval.make("C", 1, 3));
		episode.add(Interval.make("A", 3, 6));
		episode.add(Interval.make("B", 4, 9));
		episode.add(Interval.make("C", 6, 10));

		HeatmapImage.makeHeatmap("/Users/wkerr/Desktop/test1.png", signature,
				0, episode, SequenceType.allen);
	}

	public static void rmap2() {
		List<WeightedObject> signature = new ArrayList<WeightedObject>();
		signature.add(new WeightedObject(new AllenRelation("A","finishes-with", "D"), 1.0D));
		signature.add(new WeightedObject(new AllenRelation("A", "overlaps", "B"), 5.0D));
		signature.add(new WeightedObject(new AllenRelation("A", "meets", "C"),5.0D));
		signature.add(new WeightedObject(new AllenRelation("D", "overlaps", "B"), 1.0D));
		signature.add(new WeightedObject(new AllenRelation("D", "meets", "C"),1.0D));
		signature.add(new WeightedObject(new AllenRelation("B", "overlaps", "C"), 5.0D));

		List<Interval> episode = new ArrayList<Interval>();
		episode.add(Interval.make("A", 1, 5));
		episode.add(Interval.make("D", 3, 5));
		episode.add(Interval.make("B", 4, 6));
		episode.add(Interval.make("C", 4, 6));

		HeatmapImage.makeHeatmap("/Users/wkerr/Desktop/test2.png", signature,
				0, episode, SequenceType.allen);
	}

	public static void rmap3() {
		List<WeightedObject> signature = new ArrayList<WeightedObject>();
		signature.add(new WeightedObject(new AllenRelation("A","finishes-with", "D"), 1.0D));
		signature.add(new WeightedObject(new AllenRelation("A", "overlaps", "B"), 5.0D));
		signature.add(new WeightedObject(new AllenRelation("A", "meets", "C"), 5.0D));
		signature.add(new WeightedObject(new AllenRelation("D", "overlaps", "B"), 1.0D));
		signature.add(new WeightedObject(new AllenRelation("D", "meets", "C"), 1.0D));
		signature.add(new WeightedObject(new AllenRelation("B", "overlaps", "C"), 5.0D));

		List<Interval> episode = new ArrayList<Interval>();
		episode.add(Interval.make("A", 1, 5));
		episode.add(Interval.make("D", 3, 5));
		episode.add(Interval.make("B", 4, 6));
		episode.add(Interval.make("C", 5, 7));

		HeatmapImage.makeHeatmap("/Users/wkerr/Desktop/test3.png", signature,
				0, episode, SequenceType.allen);
	}
}