package org.pititom.core.event;

/**
 *
 * @author Thomas Pérennou
 */
public interface Handler<Data extends EventData<?, ?>> {
	public void handleEvent(Data data);
}
