package com.crawljax.visual;

import com.crawljax.core.state.StateVertexImpl;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

/**
 * The state vertex class which represents a state in the browser. When iterating over the possible
 * candidate elements every time a candidate is returned its removed from the list so it is a one
 * time only access to the candidates.
 */
public class StateVertexImplVisual extends StateVertexImpl {

	private static final long serialVersionUID = 123400017983489L;

	String pHashVisual;

	/**
	 * Creates a current state without an url and the stripped dom equals the dom.
	 * 
	 * @param name
	 *            the name of the state
	 * @param dom
	 *            the current DOM tree of the browser
	 */
	@VisibleForTesting
	StateVertexImplVisual(int id, String name, String dom, String pHashVisual) {
		this(id, null, name, dom, dom, pHashVisual);
	}

	/**
	 * Defines a State.
	 * 
	 * @param url
	 *            the current url of the state
	 * @param name
	 *            the name of the state
	 * @param dom
	 *            the current DOM tree of the browser
	 * @param strippedDom
	 *            the stripped dom by the OracleComparators
	 */
	public StateVertexImplVisual(int id, String url, String name, String dom, String strippedDom,
	        String pHashVisual) {
		super(id, url, name, dom, strippedDom);
		this.pHashVisual = pHashVisual;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(pHashVisual);
	}

	/**
	 * @TODO in the equals, we could also measure the distance between two pHashes
	 */
	@Override
	public boolean equals(Object object) {
		if (object instanceof StateVertexImplVisual) {
			StateVertexImplVisual that = (StateVertexImplVisual) object;
			return Objects.equal(this.pHashVisual, that.getpHashVisual());
		}
		return false;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("id", super.getId())
		        .add("name", super.getName()).add("PHASH", pHashVisual).toString();
	}

	public String getpHashVisual() {
		return pHashVisual;
	}

}
