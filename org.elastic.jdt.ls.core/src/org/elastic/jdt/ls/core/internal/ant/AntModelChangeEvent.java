package org.elastic.jdt.ls.core.internal.ant;

public class AntModelChangeEvent {

	private IAntModel fModel;
	private boolean fPreferenceChange = false;

	public AntModelChangeEvent(IAntModel model) {
		fModel = model;
	}

	public AntModelChangeEvent(IAntModel model, boolean preferenceChange) {
		fModel = model;
		fPreferenceChange = preferenceChange;
	}

	public IAntModel getModel() {
		return fModel;
	}

	/**
	 * Returns whether the Ant model has changed as a result of a preference change.
	 * 
	 * @return whether the model has changed from a preference change.
	 */
	public boolean isPreferenceChange() {
		return fPreferenceChange;
	}
}
