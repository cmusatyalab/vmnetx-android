package net.asdfa.msgpack;

/**
 * Thrown when we try to pack something we can't pack.
 * @author jon
 */
public class UnpackableItemException extends IllegalArgumentException {
	private static final long serialVersionUID = 1;

	public UnpackableItemException() {
		super();
	}
	public UnpackableItemException(String message) {
		super(message);
	}
	public UnpackableItemException(String message, Throwable cause) {
		super(message, cause);
	}
	public UnpackableItemException(Throwable cause) {
		super(cause);
	}
}
