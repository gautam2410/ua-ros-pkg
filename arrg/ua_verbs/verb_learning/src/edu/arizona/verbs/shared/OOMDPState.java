package edu.arizona.verbs.shared;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

public class OOMDPState implements Remappable<OOMDPState> {

	private List<OOMDPObjectState> objectStates_;
	private List<Relation> relations_;
	private String hashString_ = null;
	
	public OOMDPState(List<OOMDPObjectState> objectStates, List<Relation> relations) {
		objectStates_ = objectStates;
		
		Collections.sort(objectStates_); // Important!
		
		relations_ = relations;
		
		Collections.sort(relations_); // Also Important!
	}
	
	public List<OOMDPObjectState> getObjectStates() {
		return objectStates_;
	}
	
	public Set<String> getActiveRelations() {
		Set<String> result = new HashSet<String>();
		
		for (Relation rel : relations_) {
			if (rel.getValue()) { 
				result.add(rel.toString());
			}
		}
		
		return result;
	}
	
	public List<Relation> getRelations() {
		return relations_;
	}
	
	@Override
	public String toString() {
		if (hashString_ == null) {
			hashString_ = new String();
			
			for (OOMDPObjectState os : objectStates_) {
				hashString_ += os.toString();
			}
			
			for (Relation rel : relations_) {
				if (rel.getValue()) {
					hashString_ += rel;
				}
			}
		} 
		
		return hashString_;
	}
	
	@Override
	public OOMDPState remap(Map<String, String> nameMap) {
		Vector<OOMDPObjectState> newObjects = new Vector<OOMDPObjectState>();
		for (OOMDPObjectState obj : objectStates_) {
			newObjects.add(obj.remap(nameMap));
		}
		Vector<Relation> newRelations = new Vector<Relation>();
		for (Relation rel : relations_) {
			Relation newRel = new Relation();
			newRel.setRelation(rel.getRelation());
			newRel.setValue(rel.getValue());
			for (int i = 0; i < rel.getArguments().size(); i++) {
				newRel.getArguments().add(
						(nameMap.containsKey(rel.getArguments().get(i)) 
								? nameMap.get(rel.getArguments().get(i)) 
								: rel.getArguments().get(i))); 
			}
			newRelations.add(newRel);
		}
		
		OOMDPState newState = new OOMDPState(newObjects, newRelations);
		return newState;
	}
}
